package com.toefly.practice.domain.scoring;

import com.toefly.practice.domain.attempt.Answer;
import com.toefly.practice.domain.attempt.ItemOutcome;
import com.toefly.practice.domain.attempt.PinnedItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Pure function from the keys of the pinned item set and the answers to the score of every item
 * (docs/03-domain-model.md §6.5). An answer of the wrong form scores 0; an item without a key is left unscored.
 */
public final class AttemptScorer {

    private AttemptScorer() {}

    public static List<ItemOutcome> score(
            List<PinnedItem> items, Map<Integer, ItemKey> keys, Map<UUID, Answer> answers) {
        List<ItemOutcome> outcomes = new ArrayList<>(items.size());
        for (PinnedItem item : items) {
            ItemKey key = keys.get(item.ordinal());
            Answer answer = answers.get(item.itemId());
            double max = key == null ? item.maxScore() : key.maxScore();
            boolean answered = answer != null && !answer.blank();
            if (key == null || !answered) {
                outcomes.add(new ItemOutcome(item.itemId(), item.ordinal(), 0, max, answered, false, false, item.b()));
                continue;
            }
            Check check = check(key, answer);
            outcomes.add(new ItemOutcome(
                    item.itemId(),
                    item.ordinal(),
                    check.score(),
                    max,
                    true,
                    check.score() >= max,
                    check.pending(),
                    item.b()));
        }
        return outcomes;
    }

    /** Score of one answered item. */
    static Check check(ItemKey key, Answer answer) {
        return switch (key) {
            case ItemKey.SingleChoice single ->
                Check.of(answer instanceof Answer.Choice choice
                        && choice.optionIds().size() == 1
                        && single.key().equals(choice.optionIds().getFirst()));
            case ItemKey.MultiSelect multi ->
                Check.of(answer instanceof Answer.Choice choice
                        && new HashSet<>(choice.optionIds()).equals(multi.keys()));
            case ItemKey.ProseSummary summary -> proseSummary(summary, answer);
            case ItemKey.Text text -> Check.of(answer instanceof Answer.Text typed && matches(text, typed.text()));
            case ItemKey.Transformation transformation -> transformation(transformation, answer);
            case ItemKey.Sequence sequence -> Check.of(sequenceMatches(sequence, answer));
            case ItemKey.ErrorCorrection correction -> Check.of(errorCorrected(correction, answer));
        };
    }

    private static Check proseSummary(ItemKey.ProseSummary summary, Answer answer) {
        if (!(answer instanceof Answer.Choice choice) || choice.optionIds().size() > 3) {
            return Check.of(false);
        }
        long right = choice.optionIds().stream()
                .distinct()
                .filter(summary.keys()::contains)
                .count();
        return new Check(right == 3 ? 2 : right == 2 ? 1 : 0, false);
    }

    private static boolean matches(ItemKey.Text key, String typed) {
        String value = AnswerNormalizer.normalize(typed);
        if (key.lettersOnly()) {
            value = value.replace(" ", "");
        }
        for (String accepted : key.accepted()) {
            if (AnswerNormalizer.normalize(accepted).equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static Check transformation(ItemKey.Transformation key, Answer answer) {
        if (!(answer instanceof Answer.Text typed)) {
            return Check.of(false);
        }
        String value = AnswerNormalizer.normalize(stripFinalStop(typed.text()));
        boolean match = key.accepted().stream()
                .map(accepted -> AnswerNormalizer.normalize(stripFinalStop(accepted)))
                .anyMatch(value::equals);
        // a different wording may still be right: it waits for the AI check and does not count yet
        return match ? Check.of(true) : new Check(0, true);
    }

    private static String stripFinalStop(String text) {
        String value = text.strip();
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean sequenceMatches(ItemKey.Sequence key, Answer answer) {
        if (!(answer instanceof Answer.Sequence sequence)) {
            return false;
        }
        List<@Nullable String> fragments = sequence.fragmentIds();
        if (fragments.size() != key.slots() || fragments.stream().anyMatch(Objects::isNull)) {
            return false;
        }
        if (key.distractor() != null && fragments.contains(key.distractor())) {
            return false;
        }
        return key.accepted().stream().anyMatch(fragments::equals);
    }

    private static boolean errorCorrected(ItemKey.ErrorCorrection key, Answer answer) {
        if (!(answer instanceof Answer.Mapping mapping) || mapping.pairs().size() != 1) {
            return false;
        }
        Answer.Mapping.Pair pair = mapping.pairs().getFirst();
        if (!key.errorKey().equals(pair.key())) {
            return false;
        }
        String value = AnswerNormalizer.normalize(pair.value());
        Set<String> accepted = new HashSet<>();
        key.corrections().forEach(correction -> accepted.add(AnswerNormalizer.normalize(correction)));
        return accepted.contains(value);
    }

    /** Points of one answered item and whether it waits for an AI check. */
    record Check(double score, boolean pending) {
        static Check of(boolean correct) {
            return new Check(correct ? 1 : 0, false);
        }
    }
}
