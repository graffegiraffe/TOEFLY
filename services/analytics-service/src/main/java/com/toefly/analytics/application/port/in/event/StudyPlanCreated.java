package com.toefly.analytics.application.port.in.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@code toefly.planning.study-plan-created.v1}: the plan until the exam. */
public record StudyPlanCreated(
        UUID userId,
        UUID planId,
        LocalDate startDate,
        @Nullable LocalDate examDate,
        int minutesPerDay,
        String timezone,
        Instant at)
        implements AnalyticsEvent {}
