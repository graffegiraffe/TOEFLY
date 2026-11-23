package com.toefly.vocabulary.domain.wordset;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A Collins Vocabulary unit as a thematic word set (US-15.04). {@code id} equals the id of the unit in
 * content-service, so planning's {@code VOCABULARY_UNIT} task refers to the same set.
 */
public record WordSet(UUID id, int unitNo, String theme, String title, List<WordSetWord> words) {

    public static final String SOURCE_PREFIX = "Collins Vocabulary Unit ";

    public WordSet {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(title, "title");
        if (unitNo < 1 || unitNo > 20) {
            throw new IllegalArgumentException("Collins units are 1..20: " + unitNo);
        }
        words = List.copyOf(words);
    }

    /** AC-15.04.2: source label of the words added from the unit. */
    public String sourceLabel() {
        return SOURCE_PREFIX + unitNo;
    }
}
