package com.toefly.practice.application.port.out;

import com.toefly.practice.domain.attempt.Attempt;
import com.toefly.practice.domain.attempt.AttemptEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Event store of attempts (docs/04-architecture.md §6.6). Events are appended, never updated. */
public interface AttemptStorePort {

    /** Rebuilds the attempt from its snapshot and stream; empty when the stream does not exist. */
    Optional<Attempt> load(UUID attemptId);

    /**
     * Appends the pending events of {@code attempt} at its persisted version and marks them written.
     *
     * @throws ConcurrencyConflictException when another writer appended to the stream first
     */
    void append(Attempt attempt, EventMetadata metadata);

    /** Streams every event in the order of its global position, for rebuilding projections. */
    long forEachStream(Consumer<List<AttemptEvent>> stream);

    /** Physically deletes every stream of the user (GDPR, docs/04-architecture.md §6.6). */
    int eraseUser(UUID userId);

    /** Who caused the events: the user, the request chain and the trace (attempt_event.metadata). */
    record EventMetadata(UUID userId, UUID correlationId, String traceparent) {}
}
