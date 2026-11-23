package com.toefly.vocabulary.domain.entry;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A sentence the word was met in, with its source (an ItemSet, a Collins unit, a text label). */
public record EntryContext(String sentence, @Nullable UUID itemSetId, String sourceLabel) {

    public static final int MAX_SENTENCE = 300;
    public static final int MAX_LABEL = 120;

    public EntryContext {
        Objects.requireNonNull(sentence, "sentence");
        Objects.requireNonNull(sourceLabel, "sourceLabel");
        sentence = clip(sentence.strip().replaceAll("\\s+", " "), MAX_SENTENCE);
        sourceLabel = clip(sourceLabel.strip(), MAX_LABEL);
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
