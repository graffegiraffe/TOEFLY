package com.toefly.assessment.domain.submission;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A version waiting for AI (docs/06-ai-agent.md §8.3, docs/12 phase 5b decision 6): retries after
 * {@code max(Retry-After, 1-2-4-8-16 min)}, then every hour; after 24 hours the command also goes to the DLQ once.
 */
public record DeferredEvaluation(
        UUID submissionId,
        int versionNo,
        UUID userId,
        int attempts,
        Instant deferredAt,
        Instant nextAttemptAt,
        @Nullable String lastError,
        boolean deadLettered) {

    public static final Duration DEAD_LETTER_AFTER = Duration.ofHours(24);
    private static final Duration HOURLY = Duration.ofHours(1);

    public static DeferredEvaluation first(
            UUID submissionId, int versionNo, UUID userId, Instant now, @Nullable Duration retryAfter, String error) {
        return new DeferredEvaluation(
                submissionId, versionNo, userId, 1, now, now.plus(delay(1, retryAfter)), error, false);
    }

    /**
     * The command went out again: the attempt counts and the row waits for the next step of the back-off, so the job
     * does not send it twice while the worker is busy.
     */
    public DeferredEvaluation retried(Instant now) {
        int next = attempts + 1;
        return new DeferredEvaluation(
                submissionId,
                versionNo,
                userId,
                next,
                deferredAt,
                now.plus(delay(next, null)),
                lastError,
                deadLettered);
    }

    /** The retry failed again: wait the delay of this attempt, never less than Retry-After. */
    public DeferredEvaluation failedAgain(Instant now, @Nullable Duration retryAfter, String error) {
        return new DeferredEvaluation(
                submissionId,
                versionNo,
                userId,
                attempts,
                deferredAt,
                now.plus(delay(attempts, retryAfter)),
                error,
                deadLettered);
    }

    public boolean due(Instant now) {
        return !nextAttemptAt.isAfter(now);
    }

    public boolean shouldDeadLetter(Instant now) {
        return !deadLettered && !now.isBefore(deferredAt.plus(DEAD_LETTER_AFTER));
    }

    public DeferredEvaluation markDeadLettered() {
        return new DeferredEvaluation(
                submissionId, versionNo, userId, attempts, deferredAt, nextAttemptAt, lastError, true);
    }

    /** 1, 2, 4, 8, 16 minutes for attempts 1–5, then hourly; never sooner than Retry-After. */
    static Duration delay(int attempt, @Nullable Duration retryAfter) {
        Duration backoff = attempt <= 5 ? Duration.ofMinutes(1L << (attempt - 1)) : HOURLY;
        return retryAfter != null && retryAfter.compareTo(backoff) > 0 ? retryAfter : backoff;
    }
}
