package com.toefly.analytics.domain.metrics;

import java.time.LocalDate;

/**
 * {@code AiUsageDaily} (docs/03 §6.15): calls of one day, provider, model and prompt; the cost is the shadow price of
 * ai-gateway in micro-dollars, so free tiers still show what the traffic would cost.
 */
public record AiUsage(
        LocalDate day,
        String provider,
        String model,
        String promptKey,
        long tokensIn,
        long tokensOut,
        long costMicroUsd,
        int calls,
        int fallbackCalls,
        int cacheHits) {

    /** Key of calls without a prompt (speech, embeddings). */
    public static final String NO_PROMPT = "";

    public AiUsage plus(AiUsage other) {
        return new AiUsage(
                day,
                provider,
                model,
                promptKey,
                tokensIn + other.tokensIn,
                tokensOut + other.tokensOut,
                costMicroUsd + other.costMicroUsd,
                calls + other.calls,
                fallbackCalls + other.fallbackCalls,
                cacheHits + other.cacheHits);
    }
}
