package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.ActivityQueryUseCase;
import com.toefly.analytics.application.port.in.Caller;
import com.toefly.analytics.application.port.in.InvalidPeriodException;
import com.toefly.analytics.application.port.out.ActivityRepository;
import com.toefly.analytics.application.port.out.LearnerRepository;
import com.toefly.analytics.domain.activity.ActivityDay;
import com.toefly.analytics.domain.activity.TimeReport;
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

    private final ActivityRepository activity;
    private final LearnerRepository learners;
    private final Clock clock;

    public ActivityQueryService(ActivityRepository activity, LearnerRepository learners, Clock clock) {
        this.activity = activity;
        this.learners = learners;
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
        LearnerSettings settings = learners.find(userId).orElseGet(() -> LearnerSettings.unknown(userId));
        LocalDate today = settings.today(clock.instant());
        LocalDate end = to == null ? today.plusDays(1) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS) : from;
        check(start, end);
        return TimeReport.of(activity.days(userId, start, end), start, end, today, settings.minutesPerDay());
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
