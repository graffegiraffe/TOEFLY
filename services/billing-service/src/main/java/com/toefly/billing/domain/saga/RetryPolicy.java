package com.toefly.billing.domain.saga;

import java.time.Duration;

/** AC-27.03.3: a step is retried up to {@code maxAttempts} times with exponential backoff, then compensated. */
public record RetryPolicy(int maxAttempts, Duration firstDelay, Duration maxDelay) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
    }

    public static RetryPolicy standard() {
        return new RetryPolicy(5, Duration.ofSeconds(5), Duration.ofHours(1));
    }

    /** Delay before attempt {@code attempts + 1}. */
    public Duration delayAfter(int attempts) {
        long factor = 1L << Math.min(Math.max(attempts - 1, 0), 20);
        Duration delay = firstDelay.multipliedBy(factor);
        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }
}
