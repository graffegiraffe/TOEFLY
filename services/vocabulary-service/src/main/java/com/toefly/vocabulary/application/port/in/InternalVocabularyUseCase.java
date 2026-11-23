package com.toefly.vocabulary.application.port.in;

import com.toefly.vocabulary.domain.card.ReviewLog;
import com.toefly.vocabulary.domain.entry.VocabularyEntry;
import java.util.List;
import java.util.UUID;

/** Internal API (docs/05 §9.3.4, docs/06 tools {@code addWordToVocabulary}, {@code getVocabularyDue}). */
public interface InternalVocabularyUseCase {

    Summary summary(UUID userId, int recentLimit);

    Export export(UUID userId);

    record Summary(long entriesTotal, long dueCount, List<VocabularyEntry> recent, List<LapsedEntry> lapsed) {}

    record Export(UUID userId, String timezone, int newCardsPerDay, List<EntryRow> entries, List<ReviewLog> logs) {}
}
