package com.toefly.apigateway.domain;

/**
 * Token bucket: {@code replenishRate} tokens per second up to {@code burstCapacity}; a request takes
 * {@code requestedTokens}. Limits per minute or hour use a large request cost (10 per minute: rate 1, burst 10,
 * cost 6).
 */
public record TokenBucket(double replenishRate, int burstCapacity, int requestedTokens) {

    public TokenBucket {
        if (replenishRate <= 0 || burstCapacity < 1 || requestedTokens < 1 || requestedTokens > burstCapacity) {
            throw new IllegalArgumentException("Invalid token bucket " + replenishRate + "/" + burstCapacity);
        }
    }

    /** Seconds until {@code requestedTokens} are available again when {@code remaining} are left. */
    public long secondsUntilAllowed(double remaining) {
        double missing = requestedTokens - remaining;
        return missing <= 0 ? 0 : Math.max(1, (long) Math.ceil(missing / replenishRate));
    }
}
