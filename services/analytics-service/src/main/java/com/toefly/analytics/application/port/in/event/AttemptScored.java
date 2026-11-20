package com.toefly.analytics.application.port.in.event;

import com.toefly.analytics.domain.common.Section;
import com.toefly.analytics.domain.common.TaskType;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@code toefly.practice.attempt-scored.v1}: the result of a closed task. */
public record AttemptScored(
        UUID userId,
        UUID attemptId,
        TaskType taskType,
        @Nullable Section section,
        int correctCount,
        int totalCount,
        Instant at)
        implements AnalyticsEvent {}
