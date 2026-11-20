package com.toefly.analytics.application.port.in.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** {@code toefly.planning.week-published.v1}: the week and the planned day of each task. */
public record WeekPublished(UUID userId, UUID weekId, int weekNo, LocalDate day1, List<TaskDay> tasks, Instant at)
        implements AnalyticsEvent {

    public WeekPublished {
        tasks = List.copyOf(tasks);
    }

    /** A task and its day in the learner's calendar. */
    public record TaskDay(UUID weekTaskId, LocalDate localDate) {}
}
