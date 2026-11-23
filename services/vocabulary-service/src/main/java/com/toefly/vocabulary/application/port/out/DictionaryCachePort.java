package com.toefly.vocabulary.application.port.out;

import com.toefly.vocabulary.domain.entry.WordDetails;
import java.util.Optional;

/** dictionary_cache: dictionary data of a lemma for all learners (INV-VOC-06). */
public interface DictionaryCachePort {

    Optional<WordDetails> find(String lemma);

    void put(String lemma, WordDetails details, Source source, String aiStamp);

    enum Source {
        AI,
        WORD_SET
    }
}
