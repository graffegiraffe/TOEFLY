package com.toefly.practice.domain.attempt;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Result of checking one item, the input of {@link Attempt#score}. */
public record ItemOutcome(
        UUID itemId,
        int ordinal,
        double score,
        double maxScore,
        boolean answered,
        boolean correct,
        boolean pending,
        @Nullable Double b) {}
