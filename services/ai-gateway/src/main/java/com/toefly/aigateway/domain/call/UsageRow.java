package com.toefly.aigateway.domain.call;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Usage of one day, provider, model, use case and feature. */
public record UsageRow(
        LocalDate day,
        String provider,
        String model,
        String useCase,
        @Nullable String feature,
        long calls,
        long cacheHits,
        long fallbacks,
        long inputTokens,
        long outputTokens,
        double audioSeconds,
        long characters,
        long shadowCostMicroUsd) {}
