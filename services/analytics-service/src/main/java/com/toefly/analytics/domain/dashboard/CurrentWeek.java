package com.toefly.analytics.domain.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The week the learner is in: its number, first day, size and progress. */
public record CurrentWeek(
        UUID weekId,
        int weekNo,
        @Nullable LocalDate day1,
        int tasksTotal,
        double progressPercent,
        boolean completed,
        Instant asOf) {

    /** Day of the week (1–7, more for an extended week) on {@code today}; {@code null} before the first day. */
    public @Nullable Integer dayOfWeek(LocalDate today) {
        if (day1 == null || today.isBefore(day1)) {
            return null;
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(day1, today) + 1;
    }
}
