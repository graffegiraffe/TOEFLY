package com.toefly.assessment.application.port.out;

import com.toefly.assessment.domain.estimate.BandEstimate;
import com.toefly.assessment.domain.evaluation.Evaluation;
import com.toefly.assessment.domain.submission.Submission;
import java.util.UUID;

/** Events of assessment-service through the transactional outbox (docs/03-domain-model.md §5.7). */
public interface AssessmentEventPublisherPort {

    void evaluationRequested(Submission submission, int versionNo, int attemptNo, Cause cause);

    void submissionEvaluated(Submission submission, Evaluation evaluation, Cause cause);

    void bandEstimateUpdated(UUID userId, BandEstimate estimate, Cause cause);
}
