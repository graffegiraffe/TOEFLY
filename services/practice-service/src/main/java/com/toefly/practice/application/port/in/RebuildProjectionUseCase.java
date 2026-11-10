package com.toefly.practice.application.port.in;

import java.time.Instant;
import java.util.UUID;

public interface RebuildProjectionUseCase {

    /** Rebuilds attempt_view from the event store and swaps it in atomically. */
    Rebuild rebuild();

    record Rebuild(UUID jobId, Instant startedAt, Instant finishedAt, long processedEvents) {}
}
