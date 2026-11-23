package com.toefly.vocabulary.application.port.out;

import org.jspecify.annotations.Nullable;

/** A key academic word of a text with a short definition and the sentence it is used in. */
public record KeyWord(
        String lemma,
        @Nullable String partOfSpeech,
        @Nullable String definitionEn,
        @Nullable String sentence) {}
