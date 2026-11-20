package com.toefly.analytics.application.port.in.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** {@code toefly.planning.week-completed.v1}: the week closed by progress, by time or early ({@code closedBy}). */
public record WeekCompleted(
        UUID userId,
        UUID weekId,
        int weekNo,
        LocalDate startDate,
        LocalDate endDate,
        double progressPercent,
        int tasksTotal,
        int tasksDone,
        String closedBy,
        Instant at)
        implements AnalyticsEvent {

    /** {@code WeekCloseReason} of a week closed with at least 70% done. */
    public static final String BY_PROGRESS = "PROGRESS_70";

    public boolean closedByProgress() {
        return BY_PROGRESS.equals(closedBy);
    }
}
