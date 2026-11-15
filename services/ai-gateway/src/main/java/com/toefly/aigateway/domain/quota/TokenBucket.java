package com.toefly.aigateway.domain.quota;

/**
 * Continuous refill of one bucket (§2.5.1). The Valkey script {@code reserve.lua} applies the same arithmetic
 * atomically; this class is its reference and the in-memory store of the tests.
 */
public record TokenBucket(double tokens, long updatedAtMillis) {

    public static TokenBucket full(QuotaLimit limit, long nowMillis) {
        return new TokenBucket(limit.capacity(), nowMillis);
    }

    public TokenBucket refill(QuotaLimit limit, long nowMillis) {
        long elapsed = Math.max(0, nowMillis - updatedAtMillis);
        double refilled = Math.min(limit.capacity(), tokens + elapsed * limit.refillPerMilli());
        return new TokenBucket(refilled, Math.max(nowMillis, updatedAtMillis));
    }

    /** Milliseconds until the bucket holds {@code need + floor}; 0 if it already does, -1 if it never will. */
    public long waitFor(QuotaLimit limit, double need, double floor) {
        double required = need + floor;
        if (required > limit.capacity()) {
            return -1;
        }
        if (tokens >= required) {
            return 0;
        }
        return (long) Math.ceil((required - tokens) / limit.refillPerMilli());
    }

    public TokenBucket take(double amount) {
        return new TokenBucket(tokens - amount, updatedAtMillis);
    }
}
