package com.toefly.analytics.application.port.in;

/** A {@code from}/{@code to} pair the API does not serve ({@code 400 validation-failed}). */
public class InvalidPeriodException extends RuntimeException {

    public InvalidPeriodException(String message) {
        super(message);
    }
}
