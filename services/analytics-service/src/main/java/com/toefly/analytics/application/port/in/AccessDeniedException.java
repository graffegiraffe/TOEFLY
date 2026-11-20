package com.toefly.analytics.application.port.in;

/** The caller may not read the data of this learner ({@code 403 forbidden}). */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
