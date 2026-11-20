package com.toefly.analytics.domain.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One week of the plan for the weeks chart of Progress: progress, tasks and how the week closed. A completed week is
 * final; before that the newest progress wins (INV-ANL-02).
 */
public record WeekSummary(
        UUID weekId,
        UUID userId,
        int weekNo,
        @Nullable LocalDate day1,
        @Nullable LocalDate endDate,
        double progressPercent,
        int tasksTotal,
        int tasksDone,
        boolean completed,
        @Nullable String closedBy,
        Instant asOf) {

    /** Length of a regular week; the chart uses it while the week is open. */
    public static final int WEEK_DAYS = 7;

    public static WeekSummary published(UUID weekId, UUID userId, int weekNo, LocalDate day1, int tasks, Instant at) {
        return new WeekSummary(weekId, userId, weekNo, day1, null, 0, tasks, 0, false, null, at);
    }

    public WeekSummary published(LocalDate first, int tasks) {
        return new WeekSummary(
                weekId, userId, weekNo, first, endDate, progressPercent, tasks, tasksDone, completed, closedBy, asOf);
    }

    public WeekSummary taskDone(double progress, Instant at) {
        if (completed) {
            return this;
        }
        int done = tasksDone + 1;
        if (at.isBefore(asOf)) {
            return new WeekSummary(
                    weekId, userId, weekNo, day1, endDate, progressPercent, tasksTotal, done, false, closedBy, asOf);
        }
        return new WeekSummary(weekId, userId, weekNo, day1, endDate, progress, tasksTotal, done, false, closedBy, at);
    }

    public WeekSummary completed(
            LocalDate first, LocalDate end, double progress, int total, int done, String reason, Instant at) {
        return new WeekSummary(weekId, userId, weekNo, first, end, progress, total, done, true, reason, at);
    }

    /** Last day of the week: the closing day, or the seventh day while it is open. */
    public @Nullable LocalDate lastDay() {
        if (endDate != null) {
            return endDate;
        }
        return day1 == null ? null : day1.plusDays(WEEK_DAYS - 1L);
    }
}
