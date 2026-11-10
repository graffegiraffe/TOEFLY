package com.toefly.practice.application.port.out;

import com.toefly.practice.application.port.in.AttemptCursor;
import com.toefly.practice.application.port.in.AttemptQuery;
import com.toefly.practice.application.port.in.AttemptView;
import com.toefly.practice.domain.attempt.AttemptMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The attempt_view projection, written in the transaction of the events (inline projection). */
public interface AttemptViewPort {

    void upsert(AttemptView view);

    Optional<AttemptView> find(UUID attemptId);

    /** The open PRACTICE attempt of the user on the set (INV-ATT-01). */
    Optional<AttemptView> findInProgress(UUID userId, UUID itemSetId);

    boolean hasScored(UUID userId, UUID itemSetId);

    /** Records activity of the learner and the running active time (heartbeats). */
    void touch(UUID attemptId, Instant at, int activeSeconds);

    /** Keyset page ordered by {@code startedAt} and id; {@code after} is the last row of the previous page. */
    List<AttemptView> page(AttemptQuery query, @Nullable AttemptCursor after, int limit);

    /** IN_PROGRESS attempts of the mode without activity since {@code before}. */
    List<UUID> stale(AttemptMode mode, Instant before, int limit);

    /** Replaces the whole projection with {@code views} atomically (rebuild, docs/04-architecture.md §6.6). */
    void replaceAll(List<AttemptView> views);

    int eraseUser(UUID userId);
}
