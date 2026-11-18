package com.toefly.assessment.application.port.out;

import com.toefly.assessment.domain.submission.DeferredEvaluation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Table {@code deferred_evaluation}: versions waiting for AI. */
public interface DeferredEvaluationRepository {

    void save(DeferredEvaluation deferred);

    Optional<DeferredEvaluation> find(UUID submissionId, int versionNo);

    void delete(UUID submissionId, int versionNo);

    /** Due rows, locked for this transaction and skipped by other replicas. */
    List<DeferredEvaluation> lockDue(Instant now, int limit);

    List<DeferredEvaluation> page(int offset, int limit);
}
