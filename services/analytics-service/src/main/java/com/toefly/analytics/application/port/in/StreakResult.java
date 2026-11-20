package com.toefly.analytics.application.port.in;

import com.toefly.analytics.domain.activity.Streak;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Streak and the goal of today for Home (US-19.01, US-19.02). */
public record StreakResult(
        Streak streak,
        String timezone,
        LocalDate today,
        int todayActiveSeconds,
        double todayGoalProgress,
        @Nullable Instant asOf) {}
