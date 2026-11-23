package com.toefly.tutoragent.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reviews waiting for AI (docs/12, phase 5c, decision 8). */
public interface DeferredReviewRepository {

    void defer(ClosedWeek week, int attempts, Instant nextAttemptAt, String error);

    List<Deferred> due(Instant now, int limit);

    void remove(UUID weekId);

    record Deferred(ClosedWeek week, int attempts) {}
}
