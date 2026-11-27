package com.toefly.tutoragent.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Writes of the local projections and the erasure of a learner. */
public interface ProjectionRepository {

    void entitlements(UUID userId, Collection<String> entitlements, long version, Instant at);

    void examStarted(UUID userId, UUID attemptId, Instant at);

    void examEnded(UUID attemptId);

    /** True while the learner has an exam attempt that started in the last 4 hours. */
    boolean examInProgress(UUID userId, Instant now);

    void activity(UUID userId, Instant at);

    void profile(UUID userId, @Nullable String timezone, @Nullable LocalDate examDate);

    void weekProgress(UUID userId, UUID weekId, int weekNo, int progressPercent, boolean rebalanced, Instant at);

    void erase(UUID userId);
}
