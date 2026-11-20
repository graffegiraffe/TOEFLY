package com.toefly.analytics.application.port.in.event;

import com.toefly.analytics.domain.common.TaskType;
import java.time.Instant;
import java.util.UUID;

/** {@code toefly.practice.attempt-started.v1}: carries the ETS norm of the set ({@code estimatedSeconds}). */
public record AttemptStarted(UUID userId, UUID attemptId, TaskType taskType, int estimatedSeconds, Instant at)
        implements AnalyticsEvent {}
