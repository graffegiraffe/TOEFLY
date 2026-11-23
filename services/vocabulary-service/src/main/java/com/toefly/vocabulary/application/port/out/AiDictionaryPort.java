package com.toefly.vocabulary.application.port.out;

import com.toefly.vocabulary.domain.entry.WordDetails;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Dictionary data and key words through ai-gateway (AC-15.01.5, US-15.02). Failures and a disabled AI raise
 * {@link AiUnavailableException}: the word is saved without data and enriched later.
 */
public interface AiDictionaryPort {

    WordCard wordCard(String term, @Nullable String context, UUID userId);

    KeyWords keyWords(String text, int max, UUID userId);

    /** The lemma the model chose for the term, its data and the stamp of the call. */
    record WordCard(String lemma, WordDetails details, String aiStamp) {}

    record KeyWords(List<KeyWord> words, String aiStamp) {}
}
