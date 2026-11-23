package com.toefly.vocabulary.application.port.out;

import com.toefly.vocabulary.domain.card.ReviewLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** review_log, append-only (INV-VOC-04). */
public interface ReviewLogRepository {

    /** False when the grade {@code (cardId, sessionId, sequenceNo)} is already stored. */
    boolean append(ReviewLog log);

    /** New cards introduced and distinct cards graded in {@code [from, to)}. */
    DayCounts countBetween(UUID userId, Instant from, Instant to);

    List<ReviewLog> all(UUID userId);

    record DayCounts(int newIntroduced, int cardsReviewed) {}
}
