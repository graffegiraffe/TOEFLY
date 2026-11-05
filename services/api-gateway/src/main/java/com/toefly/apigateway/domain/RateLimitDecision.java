package com.toefly.apigateway.domain;

import org.jspecify.annotations.Nullable;

/** Outcome of taking tokens from a bucket; {@code retryAfterSeconds} is 0 when allowed, no bucket means no limit. */
public record RateLimitDecision(
        boolean allowed,
        long remaining,
        long retryAfterSeconds,
        @Nullable TokenBucket bucket) {

    public static RateLimitDecision unlimited() {
        return new RateLimitDecision(true, Long.MAX_VALUE, 0, null);
    }

    public static RateLimitDecision of(TokenBucket bucket, boolean allowed, double remaining) {
        return new RateLimitDecision(
                allowed, (long) Math.floor(remaining), allowed ? 0 : bucket.secondsUntilAllowed(remaining), bucket);
    }

    /** Valkey is unavailable and the group stays open (docs/10 §8.1). */
    public static RateLimitDecision degraded(TokenBucket bucket) {
        return new RateLimitDecision(true, bucket.burstCapacity(), 0, bucket);
    }
}
