package com.toefly.vocabulary.domain.fsrs;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The scheduling part of a ReviewCard. {@code stability} and {@code difficulty} are 0 for a NEW card;
 * {@code step} is the learning or relearning step, 0 outside them.
 */
public record CardMemory(
        CardState state,
        int step,
        double stability,
        double difficulty,
        Instant due,
        @Nullable Instant lastReviewAt,
        int reps,
        int lapses) {

    public CardMemory {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(due, "due");
    }

    public static CardMemory fresh(Instant createdAt) {
        return new CardMemory(CardState.NEW, 0, 0, 0, createdAt, null, 0, 0);
    }
}
