package com.toefly.practice.application.service;

import com.toefly.practice.application.port.in.AbilityChange;
import com.toefly.practice.application.port.in.AttemptReviewResult;
import com.toefly.practice.application.port.in.AttemptReviewResult.Distractor;
import com.toefly.practice.application.port.in.AttemptReviewResult.ItemReviewResult;
import com.toefly.practice.application.port.in.ItemSetContent;
import com.toefly.practice.domain.attempt.Answer;
import com.toefly.practice.domain.attempt.AttemptSnapshot;
import com.toefly.practice.domain.attempt.PinnedItem;
import com.toefly.practice.domain.scoring.ItemExplanation;
import com.toefly.practice.domain.scoring.ItemKey;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Builds the review of a scored attempt from its events and the pinned set (AC-11.01.1): the right answer, why it
 * is right and, for every wrong option, why it is wrong. A wrong option without an explanation in the content
 * marks the review as {@code explanationPending} (the AI fallback arrives with phase 5).
 */
final class ReviewAssembler {

    private ReviewAssembler() {}

    static AttemptReviewResult assemble(
            AttemptSnapshot state,
            ItemSetContent content,
            int activeSeconds,
            int audioReplays,
            @Nullable AbilityChange abilityChange) {
        List<ItemReviewResult> items = new ArrayList<>();
        boolean pending = false;
        for (PinnedItem item : state.items()) {
            ItemKey key = content.keys().keys().get(item.ordinal());
            ItemExplanation explanation = content.keys().explanations().get(item.ordinal());
            AttemptSnapshot.ItemScore score = state.scores().get(item.itemId());
            Set<String> correctOptions = correctOptions(key);
            List<Distractor> distractors = new ArrayList<>();
            for (String option : options(content.items().get(item.ordinal()), explanation)) {
                if (correctOptions.contains(option)) {
                    continue;
                }
                String why =
                        explanation == null ? null : explanation.rationales().get(option);
                if (why == null) {
                    pending = true;
                } else {
                    distractors.add(new Distractor(option, why));
                }
            }
            items.add(new ItemReviewResult(
                    item.itemId(),
                    item.ordinal(),
                    score != null && score.correct(),
                    score != null && score.pending(),
                    score == null ? 0 : score.score(),
                    score == null ? item.maxScore() : score.maxScore(),
                    state.answers().get(item.itemId()),
                    key == null ? new Answer.Text("") : key.correctAnswer(),
                    whyCorrect(explanation, correctOptions),
                    distractors));
        }
        return new AttemptReviewResult(
                state, items, activeSeconds, state.start().estimatedSeconds(), audioReplays, abilityChange, pending);
    }

    private static Set<String> correctOptions(@Nullable ItemKey key) {
        return switch (key) {
            case ItemKey.SingleChoice single -> Set.of(single.key());
            case ItemKey.MultiSelect multi -> multi.keys();
            case ItemKey.ProseSummary summary -> summary.keys();
            case null, default -> Set.of();
        };
    }

    /** Options of a choice item, from its public content or, failing that, its explanations. */
    private static Set<String> options(@Nullable Map<String, Object> content, @Nullable ItemExplanation explanation) {
        Set<String> options = new LinkedHashSet<>();
        if (content != null && content.get("options") instanceof List<?> list) {
            for (Object option : list) {
                if (option instanceof Map<?, ?> map && map.get("key") instanceof String key) {
                    options.add(key);
                }
            }
        }
        if (options.isEmpty() && explanation != null) {
            options.addAll(explanation.rationales().keySet().stream().sorted().toList());
        }
        return options;
    }

    private static @Nullable String whyCorrect(@Nullable ItemExplanation explanation, Set<String> correctOptions) {
        if (explanation == null) {
            return null;
        }
        if (explanation.summary() != null) {
            return explanation.summary();
        }
        return correctOptions.stream()
                .map(option -> explanation.rationales().get(option))
                .filter(text -> text != null)
                .findFirst()
                .orElse(null);
    }
}
