package com.toefly.practice.application.port.in;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface GetAttemptUseCase {

    AttemptDetails get(Caller caller, UUID attemptId);

    /** Review of a scored attempt; EXAM reviews open with the session result (phase 7). */
    AttemptReviewResult review(Caller caller, UUID attemptId);

    /** The learner's own attempts, or a student's attempts for a linked tutor. */
    List<AttemptView> list(
            Caller caller, @Nullable UUID studentId, AttemptQuery query, @Nullable AttemptCursor after, int limit);

    /** Attempts of any user for internal callers. */
    List<AttemptView> listOf(AttemptQuery query, int limit);

    AttemptReviewResult reviewInternal(UUID attemptId);
}
