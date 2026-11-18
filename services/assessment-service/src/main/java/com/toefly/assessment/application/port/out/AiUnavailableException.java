package com.toefly.assessment.application.port.out;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * ai-gateway did not grade: 429 quota, 503 providers, a timeout, or a refusal (400 guard, 422 schema). The version
 * becomes EVALUATION_DEFERRED and is retried (docs/06-ai-agent.md §8.3).
 */
public class AiUnavailableException extends RuntimeException {

    private final @Nullable Duration retryAfter;
    private final boolean permanent;

    public AiUnavailableException(String message, @Nullable Duration retryAfter, boolean permanent, Throwable cause) {
        super(message, cause);
        this.retryAfter = retryAfter;
        this.permanent = permanent;
    }

    public AiUnavailableException(String message, @Nullable Duration retryAfter, boolean permanent) {
        super(message);
        this.retryAfter = retryAfter;
        this.permanent = permanent;
    }

    public @Nullable Duration retryAfter() {
        return retryAfter;
    }

    public boolean permanent() {
        return permanent;
    }
}
