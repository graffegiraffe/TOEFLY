package com.toefly.common.events;

/** Domain refusal of an event (for example an unknown id); the record goes straight to {@code .dlq}. */
public class NonRetryableEventException extends RuntimeException {

    public NonRetryableEventException(String message) {
        super(message);
    }

    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
