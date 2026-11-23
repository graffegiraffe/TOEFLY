package com.toefly.vocabulary.domain.entry;

import org.jspecify.annotations.Nullable;

/**
 * Dictionary data of a lemma: shared by all learners and cached by {@code (lemma, partOfSpeech)} (INV-VOC-06).
 * Every field may be missing while the word is not enriched yet.
 */
public record WordDetails(
        @Nullable String partOfSpeech,
        @Nullable String definitionEn,
        @Nullable String exampleEn,
        @Nullable String translationRu,
        @Nullable String ipa) {

    public static final WordDetails EMPTY = new WordDetails(null, null, null, null, null);

    public boolean complete() {
        return definitionEn != null && !definitionEn.isBlank();
    }

    /** Fills the gaps of this from {@code other}; values of this win. */
    public WordDetails orElse(WordDetails other) {
        return new WordDetails(
                first(partOfSpeech, other.partOfSpeech),
                first(definitionEn, other.definitionEn),
                first(exampleEn, other.exampleEn),
                first(translationRu, other.translationRu),
                first(ipa, other.ipa));
    }

    private static @Nullable String first(@Nullable String a, @Nullable String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
