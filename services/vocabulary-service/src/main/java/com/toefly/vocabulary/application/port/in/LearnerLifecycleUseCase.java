package com.toefly.vocabulary.application.port.in;

import java.util.UUID;

/** Reactions to profile events: the learner's zone and the erasure of the account. */
public interface LearnerLifecycleUseCase {

    void preferencesUpdated(UUID userId, String timezone);

    void accountDeletionRequested(UUID userId);
}
