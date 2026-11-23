package com.toefly.vocabulary.application.port.out;

import com.toefly.vocabulary.domain.review.ReviewSession;
import java.util.Optional;
import java.util.UUID;

/** review_session; {@link #update} checks {@code version}. */
public interface ReviewSessionRepository {

    Optional<ReviewSession> find(UUID userId, UUID sessionId);

    Optional<ReviewSession> findForUpdate(UUID userId, UUID sessionId);

    void insert(ReviewSession session);

    ReviewSession update(ReviewSession session);

    void deleteAllOf(UUID userId);
}
