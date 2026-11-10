package com.toefly.practice.domain.attempt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** An answer to one item; the four forms of {@code AnswerValue} (docs/05-api-contracts.md §7.1). */
public sealed interface Answer {

    /** True when the answer carries nothing to score (an empty text, a sequence without fragments). */
    boolean blank();

    /** Chosen options: one for single choice, several for legacy multi-select questions. */
    record Choice(List<String> optionIds) implements Answer {
        public Choice {
            optionIds = List.copyOf(optionIds);
        }

        @Override
        public boolean blank() {
            return optionIds.isEmpty();
        }
    }

    /** Letters of a Complete the Words gap or a short exercise answer. */
    record Text(String text) implements Answer {
        public Text {
            Objects.requireNonNull(text);
        }

        @Override
        public boolean blank() {
            return text.isBlank();
        }
    }

    /** Fragments by slot (Build a Sentence); an empty slot is {@code null}. */
    record Sequence(List<@Nullable String> fragmentIds) implements Answer {
        public Sequence {
            fragmentIds = Collections.unmodifiableList(new ArrayList<>(fragmentIds));
        }

        @Override
        public boolean blank() {
            return fragmentIds.stream().allMatch(Objects::isNull);
        }
    }

    /** Pairs key to value: legacy tables and, for error correction, the faulty fragment to its correction. */
    record Mapping(List<Pair> pairs) implements Answer {
        public Mapping {
            pairs = List.copyOf(pairs);
        }

        @Override
        public boolean blank() {
            return pairs.isEmpty();
        }

        public record Pair(String key, String value) {}
    }
}
