package com.toefly.assessment.application.port.in;

import com.toefly.assessment.domain.evaluation.Evaluation;
import com.toefly.assessment.domain.submission.ModelAnswer;
import com.toefly.assessment.domain.submission.Submission;
import com.toefly.assessment.domain.submission.SubmissionVersion;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A submission for the tools of tutor-agent-service: the versions asked for, their evaluations and the model answer,
 * which is present only once the version it is shown with is evaluated (docs/06-ai-agent.md §4.5).
 */
public record InternalSubmissionView(
        Submission submission,
        List<SubmissionVersion> versions,
        List<Evaluation> evaluations,
        @Nullable ModelAnswer modelAnswer) {

    public InternalSubmissionView {
        versions = List.copyOf(versions);
        evaluations = List.copyOf(evaluations);
    }
}
