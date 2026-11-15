package com.toefly.aigateway.application.service;

import com.toefly.aigateway.domain.quota.ReservePolicy;
import java.time.Duration;

/**
 * Settings of the gateway core, bound from {@code toefly.ai.*} by the configuration.
 *
 * @param maxQueuedGradingPerUser learners may wait with at most this many GRADING calls (§2.5.2)
 * @param schemaRepeats repeats of one candidate after an answer that fails its schema (§2.5.3)
 * @param nerEnabled stage 3 of masking; when on and the model is down, nothing leaves (fail-closed)
 * @param requireEvals activation of a version needs a passed eval run of it (INV-AIG-01)
 * @param activateOnSync a prompt file without an active version becomes active at start (profile local only)
 * @param payloadRetention masked bodies of the journal are cleared after this period
 * @param rowRetention journal rows are deleted after this period
 * @param retryJitter pause before the single repeat of a failed provider call
 */
public record GatewaySettings(
        ReservePolicy reservePolicy,
        int maxQueuedGradingPerUser,
        int schemaRepeats,
        boolean nerEnabled,
        boolean requireEvals,
        boolean activateOnSync,
        Duration payloadRetention,
        Duration rowRetention,
        Duration retryJitter) {

    public static GatewaySettings defaults() {
        return new GatewaySettings(
                ReservePolicy.defaults(),
                2,
                2,
                true,
                true,
                false,
                Duration.ofDays(30),
                Duration.ofDays(90),
                Duration.ofMillis(300));
    }
}
