package com.toefly.vocabulary.application.port.out;

import com.toefly.vocabulary.domain.entry.VocabularyEntry;
import com.toefly.vocabulary.domain.review.ReviewSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Events of vocabulary-service through the transactional outbox. */
public interface VocabularyEventPublisherPort {

    List<String> topics();

    void wordAdded(VocabularyEntry entry, @Nullable UUID itemSetId, Instant at, @Nullable UUID correlationId);

    void reviewCompleted(ReviewSession session, int dueRemaining, int dueTomorrow, Instant at);
}
