package com.toefly.assessment.evals;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One response of an eval suite (docs/06-ai-agent.md §7.2): an anonymised draft of the author or a distortion of
 * one. {@code distortionOf} names the original for the monotonicity metric.
 */
public record EvalCase(
        String id,
        String taskType,
        String topic,
        @Nullable String taskPrompt,
        int version,
        String text,
        List<TutorError> tutorErrors,
        Map<String, Double> goldScores,
        String split,
        @Nullable String distortionOf,
        @Nullable String distortion) {

    public record TutorError(String quote, String comment, @Nullable String category) {}

    public @Nullable Double gold() {
        if (goldScores.isEmpty()) {
            return null;
        }
        List<Double> scores = goldScores.values().stream().sorted().toList();
        int n = scores.size();
        return n % 2 == 1 ? scores.get(n / 2) : (scores.get(n / 2 - 1) + scores.get(n / 2)) / 2;
    }
}
