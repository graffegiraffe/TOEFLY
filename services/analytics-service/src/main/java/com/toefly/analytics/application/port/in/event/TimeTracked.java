package com.toefly.analytics.application.port.in.event;

import com.toefly.analytics.domain.common.TaskType;
import com.toefly.analytics.domain.common.WeekSection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@code toefly.practice.time-tracked.v1}: the active time of a closed attempt on the learner's day. */
public record TimeTracked(
        UUID userId,
        UUID attemptId,
        TaskType taskType,
        @Nullable WeekSection weekSection,
        LocalDate localDate,
        int activeSeconds,
        boolean suspicious,
        Instant at)
        implements AnalyticsEvent {}
