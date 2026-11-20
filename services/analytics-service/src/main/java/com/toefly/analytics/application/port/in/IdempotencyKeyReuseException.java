package com.toefly.analytics.application.port.in;

/** The Idempotency-Key already started a rebuild of another read model ({@code 422 idempotency-key-reuse}). */
public class IdempotencyKeyReuseException extends RuntimeException {

    public IdempotencyKeyReuseException() {
        super("The Idempotency-Key was used for another read model.");
    }
}
