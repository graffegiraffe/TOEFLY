package com.toefly.vocabulary.application.service;

import com.toefly.vocabulary.application.port.out.AiDictionaryPort;
import com.toefly.vocabulary.application.port.out.AiUnavailableException;
import com.toefly.vocabulary.application.port.out.DictionaryCachePort;
import com.toefly.vocabulary.domain.entry.InvalidTermException;
import com.toefly.vocabulary.domain.entry.Lemma;
import com.toefly.vocabulary.domain.entry.WordDetails;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dictionary data of a term: the shared cache first, then the word card of ai-gateway (AC-15.01.5, INV-VOC-06).
 * Runs outside the database transaction; an AI failure gives no data, never an error.
 */
@Component
class WordData {

    private static final Logger log = LoggerFactory.getLogger(WordData.class);

    private final DictionaryCachePort cache;
    private final AiDictionaryPort ai;

    WordData(DictionaryCachePort cache, AiDictionaryPort ai) {
        this.cache = cache;
        this.ai = ai;
    }

    /** The lemma and its data; {@code details} is empty when nothing is known yet. */
    Resolved resolve(String term, @Nullable String context, UUID userId) {
        String lemma = Lemma.normalize(term);
        Optional<WordDetails> cached = cache.find(lemma);
        if (cached.isPresent()) {
            return new Resolved(lemma, cached.get(), true);
        }
        try {
            AiDictionaryPort.WordCard card = ai.wordCard(lemma, context, userId);
            String aiLemma = lemmaOr(card.lemma(), lemma);
            if (!aiLemma.equals(lemma)) {
                Optional<WordDetails> base = cache.find(aiLemma);
                if (base.isPresent()) {
                    return new Resolved(aiLemma, base.get(), true);
                }
            }
            if (card.details().complete()) {
                cache.put(aiLemma, card.details(), DictionaryCachePort.Source.AI, card.aiStamp());
            }
            return new Resolved(aiLemma, card.details(), card.details().complete());
        } catch (AiUnavailableException unavailable) {
            log.info("Word card of '{}' is not available: {}", lemma, unavailable.getMessage());
            return new Resolved(lemma, WordDetails.EMPTY, false);
        }
    }

    private static String lemmaOr(@Nullable String candidate, String fallback) {
        if (candidate == null || candidate.isBlank()) {
            return fallback;
        }
        try {
            return Lemma.normalize(candidate);
        } catch (InvalidTermException invalid) {
            return fallback;
        }
    }

    record Resolved(String lemma, WordDetails details, boolean found) {}
}
