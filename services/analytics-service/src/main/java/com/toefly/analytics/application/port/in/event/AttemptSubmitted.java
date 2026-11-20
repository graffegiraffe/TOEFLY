package com.toefly.analytics.application.port.in.event;

import java.time.Instant;
import java.util.UUID;

/** {@code toefly.practice.attempt-submitted.v1}: a submitted attempt, counted in the product metrics. */
public record AttemptSubmitted(UUID userId, UUID attemptId, Instant at) implements AnalyticsEvent {}
