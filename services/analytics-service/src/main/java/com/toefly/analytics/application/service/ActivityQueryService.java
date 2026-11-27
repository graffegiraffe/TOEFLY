package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.ActivityQueryUseCase;
import com.toefly.analytics.application.port.in.Caller;
import com.toefly.analytics.application.port.in.InvalidPeriodException;
import com.toefly.analytics.application.port.in.ReportPeriod;
import com.toefly.analytics.application.port.out.ActivityRepository;
import com.toefly.analytics.application.port.out.DashboardRepository;
import com.toefly.analytics.application.port.out.LearnerRepository;
import com.toefly.analytics.domain.activity.ActivityDay;
import com.toefly.analytics.domain.activity.TimeReport;
import com.toefly.analytics.domain.dashboard.StudentDashboard;
import com.toefly.analytics.domain.learner.LearnerSettings;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Activity days and the time report of a learner (Progress → Time, AC-16.02.1). */
@Service
public class ActivityQueryService implements ActivityQueryUseCase {

    /** Default period of the time report: the last seven days including today. */
    static final int DEFAULT_DAYS = 7;

    /** Days of {@link ReportPeriod#LAST_4_WEEKS}. */
    static final int FOUR_WEEKS_DAYS = 28;

    private final ActivityRepository activity;
    private final LearnerRepository learners;
    private final DashboardRepository dashboards;
    private final Clock clock;

    public ActivityQueryService(
            ActivityRepository activity, LearnerRepository learners, DashboardRepository dashboards, Clock clock) {
        this.activity = activity;
        this.learners = learners;
        this.dashboards = dashboards;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityDay> days(Caller caller, @Nullable UUID studentId, LocalDate from, LocalDate to) {
        UUID userId = Access.target(caller, studentId);
        check(from, to);
        return activity.days(userId, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public TimeReport timeReport(
            Caller caller, @Nullable UUID studentId, @Nullable LocalDate from, @Nullable LocalDate to) {
        UUID userId = Access.target(caller, studentId);
        LearnerSettings settings = settings(userId);
        LocalDate today = settings.today(clock.instant());
        LocalDate end = to == null ? today.plusDays(1) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS) : from;
        return report(userId, settings, today, start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public TimeReport timeReportOf(UUID userId, ReportPeriod period) {
        LearnerSettings settings = settings(userId);
        LocalDate today = settings.today(clock.instant());
        LocalDate start = switch (period) {
            case TODAY -> today;
            case THIS_WEEK ->
                DashboardQueryService.weekStart(
                        dashboards
                                .find(userId)
                                .map(StudentDashboard::currentWeek)
                                .orElse(null),
                        today);
            case LAST_4_WEEKS -> today.minusDays(FOUR_WEEKS_DAYS - 1);
        };
        return report(userId, settings, today, start, today.plusDays(1));
    }

    private LearnerSettings settings(UUID userId) {
        return learners.find(userId).orElseGet(() -> LearnerSettings.unknown(userId));
    }

    private TimeReport report(UUID userId, LearnerSettings settings, LocalDate today, LocalDate start, LocalDate end) {
        check(start, end);
        LocalDate planStart = settings.planStart();
        LocalDate planFrom = planStart != null && planStart.isAfter(start) ? planStart : start;
        return TimeReport.of(activity.days(userId, start, end), start, end, planFrom, today, settings.minutesPerDay());
    }

    static void check(LocalDate from, LocalDate to) {
        if (!from.isBefore(to)) {
            throw new InvalidPeriodException("from must be before to.");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw new InvalidPeriodException("The period is longer than " + MAX_DAYS + " days.");
        }
    }
}
