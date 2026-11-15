package com.toefly.aigateway.domain.call;

/** How a call ended; every call, a refusal included, is journaled and accounted (§2.7). */
public enum AiCallOutcome {
    SUCCESS,
    CACHE_HIT,
    QUOTA_EXHAUSTED,
    PROVIDER_ERROR,
    OUTPUT_INVALID,
    GUARD_BLOCKED
}
