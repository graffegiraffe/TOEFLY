package com.toefly.assessment.domain.estimate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Everything the estimate is computed from (docs/03-domain-model.md §8.2–8.4). Lists are newest first: Build a
 * Sentence items (last 30) and effective scores of first versions of Writing tasks.
 */
public record EstimateInputs(
        @Nullable Instant onboardingCompletedAt,
        Map<Section, Ability> abilities,
        List<Boolean> buildSentenceItems,
        List<Double> emailScores,
        List<Double> discussionScores,
        Instant asOf) {

    public record Ability(double theta, double standardError, int n) {}

    public EstimateInputs {
        abilities = Map.copyOf(abilities);
        buildSentenceItems = List.copyOf(buildSentenceItems);
        emailScores = List.copyOf(emailScores);
        discussionScores = List.copyOf(discussionScores);
    }
}
