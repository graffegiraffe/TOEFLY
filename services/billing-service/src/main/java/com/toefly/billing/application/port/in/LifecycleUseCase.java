package com.toefly.billing.application.port.in;

import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Reactions to other services' events and the timers of trials, cancellations and grace periods. */
public interface LifecycleUseCase {

    /** AC-27.02.1, AC-27.02.4, AC-27.02.5. */
    void profileCreated(UUID userId, Set<Caller.Role> roles, @Nullable UUID correlationId, UUID eventId);

    /** docs/02 §1.3 rule 1: an evaluated AI check of a Free learner counts against the limit. */
    void aiCheckEvaluated(UUID userId, boolean diagnostic, UUID eventId);

    /** docs/04 §5.11: payments are anonymized, the rest is removed, the rights topic gets a tombstone. */
    void accountDeletionRequested(UUID userId);

    /** Ends trials, canceled periods, grace periods and grants; sends TRIAL_ENDING. Returns handled subscriptions. */
    int runTimers();
}
