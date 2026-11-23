package com.toefly.vocabulary.domain.review;

/** The session is already completed. */
public class SessionStateException extends RuntimeException {

    public SessionStateException(String message) {
        super(message);
    }
}
