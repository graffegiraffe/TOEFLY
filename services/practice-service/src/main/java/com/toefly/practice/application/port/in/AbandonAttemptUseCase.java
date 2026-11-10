package com.toefly.practice.application.port.in;

import java.util.UUID;

public interface AbandonAttemptUseCase {

    /** The learner leaves an attempt for good (docs/03-domain-model.md §6.5). */
    AttemptDetails abandon(UUID userId, UUID attemptId, UUID correlationId);

    /** Abandons PRACTICE attempts idle for 7 days and EXAM attempts idle for 24 hours (AC-06.03.3). */
    int abandonStale();
}
