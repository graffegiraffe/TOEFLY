package com.toefly.tutoragent.domain.learner;

import com.toefly.tutoragent.domain.review.WeekFacts;
import com.toefly.tutoragent.domain.review.WeeklyReview;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A stored weekly review for tests of readers. */
public final class WeekFactsSupport {

    private WeekFactsSupport() {}

    public static WeeklyReview review(UUID user) {
        WeekFacts facts = new WeekFacts(
                3, 10, 8, 80.0, null, List.of(), List.of(), Map.of(), Map.of(), List.of(), List.of(), List.of());
        return new WeeklyReview(
                UUID.randomUUID(),
                user,
                UUID.randomUUID(),
                3,
                Instant.parse("2026-11-22T10:00:00Z"),
                ExplanationLanguage.EN,
                false,
                false,
                new WeeklyReview.Text(
                        "Week 3", "You finished 8 of 10 tasks.", List.of(), List.of("Steady"), List.of(), "Go on."),
                facts,
                null);
    }
}
