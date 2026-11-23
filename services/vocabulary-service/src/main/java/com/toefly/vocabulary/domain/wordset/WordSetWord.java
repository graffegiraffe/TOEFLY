package com.toefly.vocabulary.domain.wordset;

import com.toefly.vocabulary.domain.entry.WordDetails;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A word of a Collins Vocabulary unit (copy of {@code vocabulary_unit.words}). */
public record WordSetWord(
        String lemma,
        String partOfSpeech,
        String definitionEn,
        @Nullable String exampleEn,
        @Nullable String ipa) {

    public WordSetWord {
        Objects.requireNonNull(lemma, "lemma");
        Objects.requireNonNull(partOfSpeech, "partOfSpeech");
        Objects.requireNonNull(definitionEn, "definitionEn");
    }

    public WordDetails details() {
        return new WordDetails(partOfSpeech, definitionEn, exampleEn, null, ipa);
    }
}
