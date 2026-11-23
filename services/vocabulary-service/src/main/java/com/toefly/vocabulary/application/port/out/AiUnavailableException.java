package com.toefly.vocabulary.application.port.out;

/** ai-gateway is off, unreachable, out of quota or returned an unusable answer. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
