package com.toefly.vocabulary.application.port.out;

import com.toefly.vocabulary.application.port.in.LapsedEntry;
import com.toefly.vocabulary.domain.card.ReviewCard;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** review_card; {@link #update} checks {@code version}. */
public interface CardRepository {

    Optional<ReviewCard> find(UUID userId, UUID cardId);

    Optional<ReviewCard> findForUpdate(UUID userId, UUID cardId);

    Optional<ReviewCard> findByEntry(UUID entryId);

    List<ReviewCard> findAll(UUID userId, Collection<UUID> cardIds);

    /** Cards that can enter the day: not suspended, NEW or due before {@code dueBefore}. */
    List<ReviewCard> queueCandidates(UUID userId, Instant dueBefore);

    void insert(ReviewCard card);

    ReviewCard update(ReviewCard card);

    long countDue(UUID userId, Instant dueBefore);

    List<LapsedEntry> mostLapsed(UUID userId, int limit);
}
