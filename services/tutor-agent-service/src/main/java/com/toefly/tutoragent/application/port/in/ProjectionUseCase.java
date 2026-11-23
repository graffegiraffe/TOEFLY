package com.toefly.tutoragent.application.port.in;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Local projections the agent reads instead of synchronous calls (docs/04 §3.10). */
public interface ProjectionUseCase {

    void entitlementsChanged(UUID userId, Collection<String> entitlements, long version, Instant at);

    void examAttemptStarted(UUID userId, UUID attemptId, Instant at);

    void examAttemptEnded(UUID attemptId);

    void active(UUID userId, Instant at);

    void profileKnown(UUID userId, @Nullable String timezone, @Nullable LocalDate examDate);

    void weekProgress(UUID userId, UUID weekId, int weekNo, int progressPercent, boolean rebalanced, Instant at);

    /** Erases every row of the learner (docs/06 §6.6). */
    void accountDeletionRequested(UUID userId);
}
