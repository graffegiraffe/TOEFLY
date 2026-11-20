package com.toefly.analytics.application.port.in;

/** No rebuild with this id ({@code 404 not-found}). */
public class RebuildNotFoundException extends RuntimeException {

    public RebuildNotFoundException() {
        super("No such rebuild.");
    }
}
