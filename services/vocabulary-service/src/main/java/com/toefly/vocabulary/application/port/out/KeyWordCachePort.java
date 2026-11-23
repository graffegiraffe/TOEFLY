package com.toefly.vocabulary.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** key_word_cache: key words of an ItemSet (AC-15.02.1). */
public interface KeyWordCachePort {

    Optional<List<KeyWord>> find(UUID itemSetId);

    void put(UUID itemSetId, List<KeyWord> words, String aiStamp);
}
