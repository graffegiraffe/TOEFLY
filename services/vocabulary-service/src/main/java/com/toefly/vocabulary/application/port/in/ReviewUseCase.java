package com.toefly.vocabulary.application.port.in;

import com.toefly.vocabulary.domain.fsrs.Rating;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Daily FSRS reviews (US-15.03). */
public interface ReviewUseCase {

    DueView due(UUID userId);

    SessionView start(UUID userId, @Nullable UUID weekTaskId);

    SessionView session(UUID userId, UUID sessionId);

    /** AC-15.03.3; a repeated {@code (cardId, sessionId, sequenceNo)} returns the card without a second grade. */
    ReviewCardView review(UUID userId, UUID cardId, Rating rating, UUID sessionId, int sequenceNo, int elapsedMs);

    SessionView complete(UUID userId, UUID sessionId);
}
