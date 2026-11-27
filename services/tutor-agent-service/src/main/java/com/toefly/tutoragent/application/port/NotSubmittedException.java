package com.toefly.tutoragent.application.port;

/** The attempt or submission is not submitted yet: owners never give keys or samples before that (docs/06 §4.8). */
public class NotSubmittedException extends RuntimeException {

    public NotSubmittedException(String message) {
        super(message);
    }
}
