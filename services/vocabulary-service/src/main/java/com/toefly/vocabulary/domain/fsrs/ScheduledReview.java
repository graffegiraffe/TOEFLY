package com.toefly.vocabulary.domain.fsrs;

import java.time.Duration;

/**
 * Result of one grade: the new memory, the waiting time until {@code due} and the elapsed days the grade was given
 * after (the {@code ReviewLog} values of docs/03 §6.11).
 */
public record ScheduledReview(Rating rating, CardMemory after, Duration interval, double elapsedDays) {

    public double scheduledDays() {
        return interval.toMillis() / 86_400_000.0;
    }
}
