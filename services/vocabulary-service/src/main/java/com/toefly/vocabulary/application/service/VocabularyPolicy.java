package com.toefly.vocabulary.application.service;

/**
 * Switches of the service: the pronunciation button needs speech-service (phase 6) and stays hidden until then
 * (docs/12, phase 8); the enrichment job gives up on a word after {@code maxEnrichAttempts}.
 */
public record VocabularyPolicy(boolean pronunciationEnabled, int maxEnrichAttempts) {

    public static VocabularyPolicy defaults() {
        return new VocabularyPolicy(false, 5);
    }
}
