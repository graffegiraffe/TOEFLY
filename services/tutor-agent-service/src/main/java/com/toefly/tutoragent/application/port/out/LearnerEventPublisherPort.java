package com.toefly.tutoragent.application.port.out;

import com.toefly.tutoragent.domain.learner.LearnerModel;

/** Events of the learner model through the outbox. */
public interface LearnerEventPublisherPort {

    void learnerModelUpdated(LearnerModel model, Cause cause);
}
