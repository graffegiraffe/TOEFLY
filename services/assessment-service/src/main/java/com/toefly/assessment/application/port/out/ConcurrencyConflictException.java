package com.toefly.assessment.application.port.out;

/** Another writer changed the aggregate first; the command repeats on fresh state. */
public class ConcurrencyConflictException extends RuntimeException {

    public ConcurrencyConflictException(String message) {
        super(message);
    }
}
