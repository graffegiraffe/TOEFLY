package com.toefly.vocabulary.application.port.out;

import com.toefly.vocabulary.application.port.in.EntryPage;
import com.toefly.vocabulary.application.port.in.EntryQuery;
import com.toefly.vocabulary.application.port.in.EntryRow;
import com.toefly.vocabulary.domain.entry.VocabularyEntry;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** vocabulary_entry. {@link #update} checks {@code version} (optimistic lock) and returns the stored version. */
public interface EntryRepository {

    Optional<VocabularyEntry> find(UUID userId, UUID entryId);

    List<VocabularyEntry> findAll(UUID userId, Collection<UUID> entryIds);

    Optional<VocabularyEntry> findByLemma(UUID userId, String lemma);

    /** The entry of the lemma, locked for the rest of the transaction (INV-VOC-01 under concurrent saves). */
    Optional<VocabularyEntry> findByLemmaForUpdate(UUID userId, String lemma);

    /** Inserts the entry; false when the lemma was inserted by a concurrent request. */
    boolean insert(VocabularyEntry entry);

    VocabularyEntry update(VocabularyEntry entry);

    void delete(UUID userId, UUID entryId);

    Set<String> existingLemmas(UUID userId, Collection<String> lemmas);

    /** Entries still without dictionary data, oldest first, for the enrichment job. */
    List<VocabularyEntry> unenriched(int limit, int maxAttempts);

    void countEnrichAttempt(UUID entryId);

    EntryPage page(UUID userId, EntryQuery query, Instant endOfDay);

    long count(UUID userId);

    List<VocabularyEntry> recent(UUID userId, int limit);

    List<EntryRow> all(UUID userId);

    void deleteAllOf(UUID userId);
}
