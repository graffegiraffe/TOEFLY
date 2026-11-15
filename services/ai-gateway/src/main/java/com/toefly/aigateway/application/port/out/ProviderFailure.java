package com.toefly.aigateway.application.port.out;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * A provider call failed. {@code RATE_LIMITED} empties the buckets of the model until {@code retryAfter};
 * {@code UNAVAILABLE} (5xx, timeout, connection) is retried once and counts for the circuit breaker;
 * {@code REJECTED} (other 4xx) moves on to the next candidate at once.
 */
public class ProviderFailure extends RuntimeException {

    public enum Kind {
        RATE_LIMITED,
        UNAVAILABLE,
        REJECTED
    }

    private final Kind kind;
    private final @Nullable Duration retryAfter;

    public ProviderFailure(Kind kind, String message, @Nullable Duration retryAfter, @Nullable Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.retryAfter = retryAfter;
    }

    public static ProviderFailure unavailable(String message, @Nullable Throwable cause) {
        return new ProviderFailure(Kind.UNAVAILABLE, message, null, cause);
    }

    public Kind kind() {
        return kind;
    }

    public @Nullable Duration retryAfter() {
        return retryAfter;
    }
}
