package com.toefly.aigateway.domain.call;

import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.routing.AiProvider;
import com.toefly.aigateway.domain.routing.AiUseCase;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Journal row of one call (§2.8). Request and response are kept only masked; the mapping of placeholders to the
 * originals never reaches this record (INV-AIG-04).
 */
public record AiCall(
        String id,
        String callerService,
        AiUseCase useCase,
        @Nullable String feature,
        @Nullable Priority priority,
        @Nullable UUID userId,
        @Nullable String promptKey,
        @Nullable Integer promptVersion,
        AiProvider provider,
        String model,
        int fallbackDepth,
        boolean degraded,
        boolean cacheHit,
        @Nullable String requestMasked,
        @Nullable String responseMasked,
        TokenUsage usage,
        long latencyMs,
        long shadowCostMicroUsd,
        @Nullable String guardVerdict,
        AiCallOutcome outcome,
        @Nullable String errorCode,
        @Nullable String traceId,
        @Nullable UUID correlationId,
        Instant createdAt) {

    public AiStamp stamp() {
        return new AiStamp(id, promptKey, promptVersion, provider, model, fallbackDepth, degraded, cacheHit, createdAt);
    }
}
