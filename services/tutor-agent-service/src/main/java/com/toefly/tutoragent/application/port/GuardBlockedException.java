package com.toefly.tutoragent.application.port;

/** The guard of ai-gateway blocked the message or the answer (AC-17.04.2, AC-17.04.3). */
public class GuardBlockedException extends RuntimeException {

    public GuardBlockedException(String message) {
        super(message);
    }
}
