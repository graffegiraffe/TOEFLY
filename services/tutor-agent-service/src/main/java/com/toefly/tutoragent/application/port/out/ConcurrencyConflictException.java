package com.toefly.tutoragent.application.port.out;

/** Another writer changed the aggregate first. */
public class ConcurrencyConflictException extends RuntimeException {

    public ConcurrencyConflictException(String message) {
        super(message);
    }
}
