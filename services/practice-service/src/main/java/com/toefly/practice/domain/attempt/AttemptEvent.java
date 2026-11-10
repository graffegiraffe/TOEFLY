package com.toefly.practice.domain.attempt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Domain events of the attempt stream (docs/04-architecture.md §6.6). They stay inside practice_db; the integration
 * events of the canon are derived from them and go through the outbox.
 */
public sealed interface AttemptEvent {

    Instant occurredAt();

    /** First event of every stream; pins the item set version and its items (INV-ATT-05). */
    record AttemptStarted(
            UUID attemptId,
            UUID userId,
            UUID itemSetId,
            int itemSetVersion,
            TaskType taskType,
            @Nullable Section section,
            ExamFormat examFormat,
            AttemptMode mode,
            List<PinnedItem> items,
            @Nullable UUID weekTaskId,
            @Nullable UUID examSessionId,
            @Nullable UUID moduleId,
            @Nullable Instant deadlineAt,
            int estimatedSeconds,
            double timingMultiplier,
            String timeZone,
            boolean retry,
            Instant occurredAt)
            implements AttemptEvent {
        public AttemptStarted {
            items = List.copyOf(items);
        }
    }

    record AnswerRecorded(
            UUID itemId,
            Answer answer,
            @Nullable Instant clientTime,
            @Nullable Long answeredInMs,
            Instant occurredAt) implements AttemptEvent {}

    record AnswerCleared(UUID itemId, Instant occurredAt) implements AttemptEvent {}

    record AttemptPaused(Instant occurredAt) implements AttemptEvent {}

    record AttemptResumed(Instant occurredAt) implements AttemptEvent {}

    /** Answers are frozen; {@code activeSeconds} is the tracker total at submission. */
    record AttemptSubmitted(SubmitReason reason, int activeSeconds, Instant occurredAt) implements AttemptEvent {}

    /** One per item; {@code pending} marks an answer delegated to AI checking (AC-14.04.3). */
    record ItemScored(
            UUID itemId,
            int ordinal,
            double score,
            double maxScore,
            boolean answered,
            boolean correct,
            boolean pending,
            @Nullable Double b,
            Instant occurredAt)
            implements AttemptEvent {}

    record AttemptScored(double rawScore, double maxScore, int correctCount, int unansweredCount, Instant occurredAt)
            implements AttemptEvent {}

    record AttemptAbandoned(AbandonReason reason, Instant occurredAt) implements AttemptEvent {}
}
