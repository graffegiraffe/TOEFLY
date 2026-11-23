package com.toefly.vocabulary.domain.review;

import com.toefly.vocabulary.domain.fsrs.CardState;
import com.toefly.vocabulary.domain.fsrs.Rating;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A review session of the day (docs/05 §6.10): the planned cards, the grades counted so far and, once completed, the
 * figures of {@code toefly.vocabulary.review-completed.v1}.
 */
public record ReviewSession(
        UUID id,
        UUID userId,
        @Nullable UUID weekTaskId,
        LocalDate localDate,
        String timezone,
        List<UUID> plannedCardIds,
        Set<UUID> reviewedCardIds,
        int grades,
        int againCount,
        int newIntroduced,
        SessionStatus status,
        Instant startedAt,
        @Nullable Instant completedAt,
        int activeSeconds,
        int version) {

    public ReviewSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(localDate, "localDate");
        Objects.requireNonNull(timezone, "timezone");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startedAt, "startedAt");
        plannedCardIds = List.copyOf(plannedCardIds);
        reviewedCardIds = Set.copyOf(reviewedCardIds);
    }

    public static ReviewSession start(
            UUID id, UUID userId, @Nullable UUID weekTaskId, DailyQueue queue, String timezone, Instant now) {
        return new ReviewSession(
                id,
                userId,
                weekTaskId,
                queue.localDate(),
                timezone,
                queue.cards().stream().map(card -> card.id()).toList(),
                Set.of(),
                0,
                0,
                0,
                SessionStatus.IN_PROGRESS,
                now,
                null,
                0,
                0);
    }

    public boolean planned(UUID cardId) {
        return plannedCardIds.contains(cardId);
    }

    /** Counts one grade of the session. */
    public ReviewSession graded(UUID cardId, Rating rating, CardState stateBefore, int durationMs) {
        requireOpen();
        Set<UUID> reviewed = new LinkedHashSet<>(reviewedCardIds);
        reviewed.add(cardId);
        int seconds = activeSeconds + Math.max(0, Math.min(durationMs, 600_000)) / 1000;
        return new ReviewSession(
                id,
                userId,
                weekTaskId,
                localDate,
                timezone,
                plannedCardIds,
                reviewed,
                grades + 1,
                againCount + (rating == Rating.AGAIN ? 1 : 0),
                newIntroduced + (stateBefore == CardState.NEW ? 1 : 0),
                status,
                startedAt,
                completedAt,
                seconds,
                version);
    }

    /** All planned cards were graded at least once. */
    public boolean allPlannedReviewed() {
        return reviewedCardIds.containsAll(plannedCardIds);
    }

    public ReviewSession complete(Instant now) {
        requireOpen();
        return new ReviewSession(
                id,
                userId,
                weekTaskId,
                localDate,
                timezone,
                plannedCardIds,
                reviewedCardIds,
                grades,
                againCount,
                newIntroduced,
                SessionStatus.COMPLETED,
                startedAt,
                now,
                activeSeconds,
                version);
    }

    private void requireOpen() {
        if (status == SessionStatus.COMPLETED) {
            throw new SessionStateException("The review session is already completed.");
        }
    }
}
