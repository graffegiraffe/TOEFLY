package com.toefly.planning.domain.learner;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The local projection of {@code toefly.tutor-agent.learner-model-updated.v1}: the top subcategories of mistakes and
 * the band estimate, so planning never asks tutor-agent synchronously (docs/06-ai-agent.md §5.2).
 *
 * @param version version of the learner model; an older event never replaces a newer one
 * @param mistakes subcategories, the highest priority first
 * @param bands band by section and {@code OVERALL}
 */
public record LearnerModelView(
        UUID userId, long version, Instant updatedAt, List<MistakeStat> mistakes, Map<String, Double> bands) {

    public LearnerModelView {
        mistakes = mistakes.stream()
                .sorted(Comparator.comparingDouble(MistakeStat::priority).reversed())
                .toList();
        bands = Map.copyOf(bands);
    }

    public static LearnerModelView empty(UUID userId) {
        return new LearnerModelView(userId, 0, Instant.EPOCH, List.of(), Map.of());
    }

    /** Recurring grammar topics of the last week, the most important first (AC-14.05.2). */
    public List<MistakeStat> grammarPractice() {
        return mistakes.stream().filter(MistakeStat::needsGrammarPractice).toList();
    }

    /** Unresolved subcategories, the highest priority first. */
    public List<MistakeStat> active() {
        return mistakes.stream().filter(stat -> !stat.resolved()).toList();
    }
}
