package com.toefly.assessment.application.port.in;

import com.toefly.assessment.domain.submission.DeferredEvaluation;
import java.util.List;
import java.util.UUID;

/** Retries of versions waiting for AI and the admin queue (docs/05-api-contracts.md §6.5). */
public interface DeferredEvaluationUseCase {

    /** Sends the due retries again; returns how many. */
    int retryDue();

    List<DeferredEvaluation> page(int offset, int limit);

    void retryNow(UUID submissionId);
}
