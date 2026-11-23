package com.toefly.vocabulary.application.port.out;

/**
 * ai-gateway is off, unreachable, out of quota or returned an unusable answer. {@code permanent} — the request itself
 * was refused (4xx other than 429): retrying the same word will not help, other words may still work.
 */
public class AiUnavailableException extends RuntimeException {

    private final boolean permanent;

    public AiUnavailableException(String message) {
        this(message, false, null);
    }

    public AiUnavailableException(String message, boolean permanent, Throwable cause) {
        super(message, cause);
        this.permanent = permanent;
    }

    public boolean permanent() {
        return permanent;
    }
}
