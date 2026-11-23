package com.toefly.tutoragent.application.port.out;

import com.toefly.tutoragent.domain.learner.LearnerModel;
import java.util.Optional;
import java.util.UUID;

/** Learner models; {@link #save} rejects a stale version with {@link ConcurrencyConflictException}. */
public interface LearnerModelRepository {

    Optional<LearnerModel> find(UUID userId);

    void save(LearnerModel model);

    /** Copy of the model at the end of a week, for the dynamics of reviews (docs/06 §6.1). */
    void snapshot(LearnerModel model, int weekNo);

    Optional<LearnerModel> snapshotOf(UUID userId, int weekNo);
}
