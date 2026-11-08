package com.toefly.content.application.seed;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A verified {@code GRAMMAR_UNIT} or {@code VOCABULARY_UNIT} envelope. */
public record SeedUnit(
        Kind kind,
        String seedId,
        UUID id,
        String sourceCode,
        boolean reviewed,
        String contentHash,
        int unitNo,
        @Nullable String framework,
        @Nullable String theme,
        String title,
        List<String> topicCodes,
        @Nullable String ruleCardJson,
        @Nullable Integer progressCheckNo,
        String wordsJson,
        String exercisesJson,
        Extraction extraction) {

    public enum Kind {
        GRAMMAR_UNIT,
        VOCABULARY_UNIT
    }
}
