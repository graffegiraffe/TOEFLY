package com.toefly.practice.application.port.in;

import com.toefly.practice.domain.attempt.AttemptMode;
import com.toefly.practice.domain.attempt.AttemptStatus;
import com.toefly.practice.domain.attempt.Section;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Filters of an attempt list (docs/05-api-contracts.md §7.1, listAttempts). */
public record AttemptQuery(
        UUID userId,
        @Nullable UUID itemSetId,
        @Nullable UUID weekTaskId,
        @Nullable AttemptMode mode,
        Set<AttemptStatus> statuses,
        @Nullable Section section,
        @Nullable Instant from,
        @Nullable Instant to,
        boolean ascending) {

    public AttemptQuery {
        statuses = Set.copyOf(statuses);
    }

    public AttemptQuery forUser(UUID user) {
        return new AttemptQuery(user, itemSetId, weekTaskId, mode, statuses, section, from, to, ascending);
    }
}
