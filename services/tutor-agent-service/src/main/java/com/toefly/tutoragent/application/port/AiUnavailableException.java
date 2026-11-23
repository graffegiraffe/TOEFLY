package com.toefly.tutoragent.application.port;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/** ai-gateway gave no answer: quota, outage or a refusal (docs/06 §8.3). */
public class AiUnavailableException extends RuntimeException {

    private final @Nullable Duration retryAfter;
    private final boolean rejected;

    public AiUnavailableException(
            String message, @Nullable Duration retryAfter, boolean rejected, @Nullable Throwable cause) {
        super(message, cause);
        this.retryAfter = retryAfter;
        this.rejected = rejected;
    }

    public @Nullable Duration retryAfter() {
        return retryAfter;
    }

    /** The request itself was refused (guard, validation); repeating it does not help. */
    public boolean rejected() {
        return rejected;
    }
}
