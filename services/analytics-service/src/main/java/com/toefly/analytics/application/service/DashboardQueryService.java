package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.Caller;
import com.toefly.analytics.application.port.in.DashboardQueryUseCase;
import com.toefly.analytics.application.port.in.StreakResult;
import com.toefly.analytics.application.port.in.StudentDashboardResult;
import com.toefly.analytics.application.port.out.ActivityRepository;
import com.toefly.analytics.application.port.out.DashboardRepository;
import com.toefly.analytics.application.port.out.LearnerRepository;
import com.toefly.analytics.domain.activity.ActivityDay;
import com.toefly.analytics.domain.activity.Streak;
import com.toefly.analytics.domain.dashboard.CurrentWeek;
import com.toefly.analytics.domain.dashboard.StudentDashboard;
import com.toefly.analytics.domain.dashboard.WeeklyProgress;
import com.toefly.analytics.domain.learner.LearnerSettings;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the student dashboard and the streak from the projections; nothing here calls another service. */
@Service
public class DashboardQueryService implements DashboardQueryUseCase {

    private final DashboardRepository dashboards;
    private final ActivityRepository activity;
    private final LearnerRepository learners;
    private final Clock clock;

    public DashboardQueryService(
            DashboardRepository dashboards, ActivityRepository activity, LearnerRepository learners, Clock clock) {
        this.dashboards = dashboards;
        this.activity = activity;
        this.learners = learners;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentDashboardResult student(Caller caller, @Nullable UUID studentId) {
        return studentOf(Access.target(caller, studentId));
    }

    @Override
    @Transactional(readOnly = true)
    public StudentDashboardResult studentOf(UUID userId) {
        Instant now = clock.instant();
        LearnerSettings settings = settings(userId);
        LocalDate today = settings.today(now);
        StudentDashboard dashboard = dashboards.find(userId).orElseGet(() -> StudentDashboard.empty(userId));
        ActivityDay todayActivity = activity.findDay(userId, today).orElse(null);
        LocalDate weekStart = weekStart(dashboard.currentWeek(), today);
        int weekSeconds = activity.days(userId, weekStart, today.plusDays(1)).stream()
                .mapToInt(ActivityDay::activeSeconds)
                .sum();
        Streak streak = Streak.of(activity.studyDays(userId), today, settings.minutesPerDay());
        return new StudentDashboardResult(
                dashboard,
                settings,
                today,
                todayActivity,
                weekSeconds,
                streak,
                WeeklyProgress.of(dashboards.weeks(userId), dashboards.bandPoints(userId), settings.timezone()),
                settings.daysUntilExam(now),
                latest(
                        dashboard.asOf(),
                        activity.streakAsOf(userId).orElse(null),
                        todayActivity == null ? null : todayActivity.lastActiveAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public StreakResult streak(Caller caller) {
        UUID userId = Access.target(caller, null);
        LearnerSettings settings = settings(userId);
        LocalDate today = settings.today(clock.instant());
        ActivityDay day = activity.findDay(userId, today)
                .orElseGet(() -> ActivityDay.start(userId, today, settings.minutesPerDay()));
        Streak streak = Streak.of(activity.studyDays(userId), today, day.goalMinutes());
        return new StreakResult(
                streak,
                settings.timezone().getId(),
                today,
                day.activeSeconds(),
                day.goalProgress(),
                latest(activity.streakAsOf(userId).orElse(null), day.lastActiveAt()));
    }

    private LearnerSettings settings(UUID userId) {
        return learners.find(userId).orElseGet(() -> LearnerSettings.unknown(userId));
    }

    /** "Time this week": from the first day of the current week, or the last seven days without one. */
    static LocalDate weekStart(@Nullable CurrentWeek week, LocalDate today) {
        if (week != null
                && week.day1() != null
                && !week.completed()
                && !week.day1().isAfter(today)) {
            return week.day1();
        }
        return today.minusDays(6);
    }

    static @Nullable Instant latest(@Nullable Instant... moments) {
        return Stream.of(moments)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
    }
}
