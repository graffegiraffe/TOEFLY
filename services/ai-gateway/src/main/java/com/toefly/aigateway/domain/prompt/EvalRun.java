package com.toefly.aigateway.domain.prompt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Report of one eval run of a prompt version (§7.4). */
public record EvalRun(
        UUID id,
        String promptKey,
        int promptVersion,
        String suite,
        String mode,
        boolean passed,
        Map<String, Double> metrics,
        @Nullable String reportUrl,
        Instant ranAt,
        Instant receivedAt) {

    public EvalRun {
        metrics = Map.copyOf(metrics);
    }
}
