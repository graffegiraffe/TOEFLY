package com.toefly.assessment.application.port.in;

import com.toefly.assessment.application.port.out.TaskContent;
import com.toefly.assessment.domain.evaluation.Evaluation;
import com.toefly.assessment.domain.submission.DeferredEvaluation;
import com.toefly.assessment.domain.submission.Submission;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A submission as the review shows it: versions, all evaluations, the task and a pending retry. */
public record SubmissionView(
        Submission submission,
        List<Evaluation> evaluations,
        @Nullable TaskContent task,
        @Nullable DeferredEvaluation deferred) {

    public SubmissionView {
        evaluations = List.copyOf(evaluations);
    }
}
