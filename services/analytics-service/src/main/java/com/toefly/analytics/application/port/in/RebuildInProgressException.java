package com.toefly.analytics.application.port.in;

/** A rebuild of the same read model is still running ({@code 409 conflict}). */
public class RebuildInProgressException extends RuntimeException {

    public RebuildInProgressException(ProjectionName projection) {
        super("A rebuild of " + projection.path() + " is already running.");
    }
}
