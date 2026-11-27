package com.toefly.planning.application.port.out;

import com.toefly.planning.domain.learner.LearnerModelView;
import java.util.Optional;
import java.util.UUID;

/** {@code learner_model_view}: the learner model of tutor-agent as planning last received it. */
public interface LearnerModelPort {

    /** Keeps the newest version; an older or equal version changes nothing. */
    void save(LearnerModelView view);

    Optional<LearnerModelView> find(UUID userId);

    void deleteByUser(UUID userId);
}
