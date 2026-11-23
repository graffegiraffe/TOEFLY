package com.toefly.vocabulary.application.port.in;

import com.toefly.vocabulary.domain.card.VocabularyStatus;
import com.toefly.vocabulary.domain.entry.EntryOrigin;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Filters of "My words" (AC-15.06.1): search by lemma, status, source, word set, due by the end of the local day; sorting by
 * date or difficulty; offset paging.
 */
public record EntryQuery(
        @Nullable String q,
        @Nullable VocabularyStatus status,
        @Nullable EntryOrigin origin,
        @Nullable UUID wordSetId,
        boolean dueToday,
        Sort sort,
        int offset,
        int limit) {

    public enum Sort {
        CREATED_DESC,
        CREATED_ASC,
        DIFFICULTY_DESC,
        LEMMA
    }
}
