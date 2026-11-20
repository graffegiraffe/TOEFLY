package com.toefly.analytics.application.port.in;

import com.toefly.analytics.domain.activity.ActivityDay;
import com.toefly.analytics.domain.activity.Streak;
import com.toefly.analytics.domain.dashboard.StudentDashboard;
import com.toefly.analytics.domain.dashboard.WeeklyProgress;
import com.toefly.analytics.domain.learner.LearnerSettings;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Everything the student dashboard shows, read from the projections with the time of the last applied event. */
public record StudentDashboardResult(
        StudentDashboard dashboard,
        LearnerSettings settings,
        LocalDate today,
        @Nullable ActivityDay todayActivity,
        int weekActiveSeconds,
        Streak streak,
        List<WeeklyProgress> weeks,
        @Nullable Integer examCountdownDays,
        @Nullable Instant asOf) {

    public StudentDashboardResult {
        weeks = List.copyOf(weeks);
    }
}
