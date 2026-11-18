package com.toefly.assessment.application.port.in;

import com.toefly.assessment.application.port.out.Cause;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Worker of {@code evaluation-requested}: grades the version outside a transaction, then commits the result through
 * {@code commitOnce}, which records the event in {@code processed_events} in the same transaction.
 */
public interface EvaluateSubmissionUseCase {

    void evaluate(UUID submissionId, int versionNo, Cause cause, Predicate<Runnable> commitOnce);
}
