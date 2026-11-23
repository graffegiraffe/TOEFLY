package com.toefly.tutoragent.application.port;

/** An owner refused a change by its rules, for example the limit of agent tasks in a week (409 or 422). */
public class OwnerRefusedException extends RuntimeException {

    public OwnerRefusedException(String message) {
        super(message);
    }
}
