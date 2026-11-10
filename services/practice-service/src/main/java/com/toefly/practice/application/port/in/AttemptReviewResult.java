package com.toefly.practice.application.port.in;

import com.toefly.practice.domain.attempt.Answer;
import com.toefly.practice.domain.attempt.AttemptSnapshot;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Review of a scored attempt (AC-11.01.1): score, time against the ETS norm and every item explained. */
public record AttemptReviewResult(
        AttemptSnapshot attempt,
        List<ItemReviewResult> items,
        int activeSeconds,
        int estimatedSeconds,
        int audioReplays,
        @Nullable AbilityChange abilityChange,
        boolean explanationPending) {

    public AttemptReviewResult {
        items = List.copyOf(items);
    }

    public record ItemReviewResult(
            UUID itemId,
            int ordinal,
            boolean correct,
            boolean pending,
            double score,
            double maxScore,
            @Nullable Answer userAnswer,
            Answer correctAnswer,
            @Nullable String explanation,
            List<Distractor> distractors) {

        public ItemReviewResult {
            distractors = List.copyOf(distractors);
        }
    }

    /** Why a wrong option is wrong. */
    public record Distractor(String optionId, String text) {}
}
