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
            String aiLemma = lemmaOf(card.lemma(), lemma);
            if (aiLemma == null) {
                log.warn("Word card for '{}' named another word; the data is dropped", lemma);
                return new Resolved(lemma, WordDetails.EMPTY, false);
            }
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

    /** The model's lemma; the term itself when it gave none, null when it named an unrelated word. */
    private static @Nullable String lemmaOf(@Nullable String candidate, String term) {
        if (candidate == null || candidate.isBlank()) {
            return term;
        }
        try {
            String lemma = Lemma.normalize(candidate);
            return related(lemma, term) ? lemma : null;
        } catch (InvalidTermException invalid) {
            return term;
        }
    }

    /** The model may only reduce an inflection ("mitigating" → "mitigate"), never swap the word. */
    static boolean related(String lemma, String term) {
        int stem = Math.min(3, Math.min(lemma.length(), term.length()));
        return lemma.regionMatches(0, term, 0, stem);
    }

    record Resolved(String lemma, WordDetails details, boolean found) {}
}
