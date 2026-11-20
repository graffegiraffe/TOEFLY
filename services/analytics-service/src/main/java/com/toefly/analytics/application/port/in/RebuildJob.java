package com.toefly.analytics.application.port.in;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A rebuild of one read model from {@code event_journal}. */
public record RebuildJob(
        UUID id,
        ProjectionName projection,
        Status status,
        UUID requestedBy,
        Instant requestedAt,
        @Nullable Instant finishedAt,
        long eventsReplayed,
        @Nullable String error) {

    public enum Status {
        RUNNING,
        COMPLETED,
        FAILED
    }

    public RebuildJob completed(long events, Instant at) {
        return new RebuildJob(id, projection, Status.COMPLETED, requestedBy, requestedAt, at, events, null);
    }

    public RebuildJob failed(String message, Instant at) {
        return new RebuildJob(id, projection, Status.FAILED, requestedBy, requestedAt, at, eventsReplayed, message);
    }
}
