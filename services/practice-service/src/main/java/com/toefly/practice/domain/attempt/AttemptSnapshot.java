package com.toefly.practice.domain.attempt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Immutable state of an attempt at a stream version: what the projection stores and what a snapshot restores
 * (docs/04-architecture.md §6.6).
 */
public record AttemptSnapshot(
        AttemptEvent.AttemptStarted start,
        AttemptStatus status,
        Map<UUID, Answer> answers,
        Map<UUID, Instant> answeredAt,
        int answerChanges,
        boolean paused,
        @Nullable Instant pausedSince,
        long pausedSeconds,
        @Nullable Instant submittedAt,
        @Nullable SubmitReason submitReason,
        int activeSecondsAtSubmit,
        Map<UUID, ItemScore> scores,
        @Nullable Instant scoredAt,
        @Nullable Double rawScore,
        int correctCount,
        int unansweredCount,
        @Nullable Instant abandonedAt,
        int version) {

    public AttemptSnapshot {
        answers = Map.copyOf(answers);
        answeredAt = Map.copyOf(answeredAt);
        scores = Map.copyOf(scores);
    }

    public UUID attemptId() {
        return start.attemptId();
    }

    public List<PinnedItem> items() {
        return start.items();
    }

    /** Score of one item as recorded by ItemScored. */
    public record ItemScore(
            int ordinal,
            double score,
            double maxScore,
            boolean answered,
            boolean correct,
            boolean pending,
            @Nullable Double b) {}
}
