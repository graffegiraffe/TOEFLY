package com.toefly.analytics.application.port.in.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** {@code toefly.planning.week-task-completed.v1}: a task done on {@code localDate} of the learner. */
public record WeekTaskCompleted(
        UUID userId, UUID weekId, UUID weekTaskId, int weekNo, LocalDate localDate, double progressPercent, Instant at)
        implements AnalyticsEvent {}
