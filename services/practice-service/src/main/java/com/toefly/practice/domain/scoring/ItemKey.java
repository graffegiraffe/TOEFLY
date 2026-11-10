package com.toefly.practice.domain.scoring;

import com.toefly.practice.domain.attempt.Answer;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Answer key of one item, read from the pinned item set (docs/03-domain-model.md §6.5, scoring rules). Each kind
 * knows its maximum score and the answer shown as correct in the review.
 */
public sealed interface ItemKey {

    int ordinal();

    default double maxScore() {
        return 1;
    }

    /** The answer the review shows as correct. */
    Answer correctAnswer();

    /** Multiple choice with one key: Reading, Listening, legacy single, choice exercises, insert sentence. */
    record SingleChoice(int ordinal, String key) implements ItemKey {
        @Override
        public Answer correctAnswer() {
            return new Answer.Choice(List.of(key));
        }
    }

    /** Legacy prose summary: 2 points for three right choices, 1 for two, otherwise 0. */
    record ProseSummary(int ordinal, Set<String> keys) implements ItemKey {
        public ProseSummary {
            keys = Set.copyOf(keys);
        }

        @Override
        public double maxScore() {
            return 2;
        }

        @Override
        public Answer correctAnswer() {
            return new Answer.Choice(keys.stream().sorted().toList());
        }
    }

    /** Legacy multi-select: 1 point only for the exact set. */
    record MultiSelect(int ordinal, Set<String> keys) implements ItemKey {
        public MultiSelect {
            keys = Set.copyOf(keys);
        }

        @Override
        public Answer correctAnswer() {
            return new Answer.Choice(keys.stream().sorted().toList());
        }
    }

    /**
     * A typed answer compared after normalization: the missing letters of a Complete the Words gap
     * ({@code lettersOnly}) or a word of a gap-fill or word-formation exercise.
     */
    record Text(int ordinal, List<String> accepted, boolean lettersOnly) implements ItemKey {
        public Text {
            accepted = List.copyOf(accepted);
        }

        @Override
        public Answer correctAnswer() {
            return new Answer.Text(accepted.getFirst());
        }
    }

    /** Transformation: a mismatch goes to AI checking and stays pending until then (AC-14.04.3). */
    record Transformation(int ordinal, List<String> accepted) implements ItemKey {
        public Transformation {
            accepted = List.copyOf(accepted);
        }

        @Override
        public Answer correctAnswer() {
            return new Answer.Text(accepted.getFirst());
        }
    }

    /** Build a Sentence: the chunk sequence must be a key, every slot filled, no distractor (AC-09.01.4). */
    record Sequence(
            int ordinal,
            List<List<String>> accepted,
            @Nullable String distractor,
            int slots) implements ItemKey {
        public Sequence {
            accepted = accepted.stream().map(List::copyOf).toList();
        }

        @Override
        public Answer correctAnswer() {
            return new Answer.Sequence(List.copyOf(accepted.getFirst()));
        }
    }

    /** Error correction: the faulty fragment and an accepted correction (AC-14.04.2). */
    record ErrorCorrection(int ordinal, String errorKey, List<String> corrections) implements ItemKey {
        public ErrorCorrection {
            corrections = List.copyOf(corrections);
        }

        @Override
        public Answer correctAnswer() {
            return new Answer.Mapping(List.of(new Answer.Mapping.Pair(errorKey, corrections.getFirst())));
        }
    }
}
