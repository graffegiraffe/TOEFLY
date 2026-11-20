package com.toefly.analytics.application.port.in.event;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@code toefly.ai-gateway.usage-recorded.v1}: one AI call with its tokens and shadow cost. */
public record UsageRecorded(
        @Nullable UUID userId,
        String provider,
        String model,
        @Nullable String promptKey,
        int inputTokens,
        int outputTokens,
        long costMicroUsd,
        int fallbackDepth,
        boolean cacheHit,
        Instant at)
        implements AnalyticsEvent {}
