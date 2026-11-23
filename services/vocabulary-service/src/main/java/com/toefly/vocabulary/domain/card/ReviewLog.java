package com.toefly.vocabulary.domain.card;

import com.toefly.vocabulary.domain.fsrs.CardState;
import com.toefly.vocabulary.domain.fsrs.Rating;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One grade, append-only (INV-VOC-04); counted once per {@code (cardId, sessionId, sequenceNo)}. */
public record ReviewLog(
        UUID id,
        UUID cardId,
        UUID userId,
        Rating rating,
        CardState stateBefore,
        CardState stateAfter,
        double stabilityBefore,
        double difficultyBefore,
        double stabilityAfter,
        double difficultyAfter,
        double elapsedDays,
        double scheduledDays,
        @Nullable UUID sessionId,
        int sequenceNo,
        Instant reviewedAt,
        int durationMs) {

    public ReviewLog {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cardId, "cardId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(rating, "rating");
        Objects.requireNonNull(stateBefore, "stateBefore");
        Objects.requireNonNull(stateAfter, "stateAfter");
        Objects.requireNonNull(reviewedAt, "reviewedAt");
    }
}
