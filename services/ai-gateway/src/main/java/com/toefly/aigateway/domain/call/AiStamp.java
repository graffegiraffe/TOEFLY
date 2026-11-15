package com.toefly.aigateway.domain.call;

import com.toefly.aigateway.domain.routing.AiProvider;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Provenance kept by every stored AI result (§2.9). */
public record AiStamp(
        String aiCallId,
        @Nullable String promptKey,
        @Nullable Integer promptVersion,
        AiProvider provider,
        String model,
        int fallbackDepth,
        boolean degraded,
        boolean cacheHit,
        Instant createdAt) {}
