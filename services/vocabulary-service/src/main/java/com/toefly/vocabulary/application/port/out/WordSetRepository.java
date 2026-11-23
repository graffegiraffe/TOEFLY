package com.toefly.vocabulary.application.port.out;

import com.toefly.vocabulary.domain.wordset.WordSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** word_set and word_set_word. */
public interface WordSetRepository {

    List<WordSet> all();

    Optional<WordSet> find(UUID wordSetId);

    /** Replaces the set and its words. */
    void upsert(WordSet set, Instant syncedAt);

    /** Per set: words of the set in the learner's dictionary and those mastered. */
    Map<UUID, Progress> progress(UUID userId);

    record Progress(int inDictionary, int learned) {}
}
