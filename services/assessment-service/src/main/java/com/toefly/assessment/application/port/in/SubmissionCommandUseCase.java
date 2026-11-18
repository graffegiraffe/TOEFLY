package com.toefly.assessment.application.port.in;

import com.toefly.assessment.domain.evaluation.Evaluation;
import com.toefly.assessment.domain.submission.Submission;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Commands of the Writing loop (docs/05-api-contracts.md §6.5). */
public interface SubmissionCommandUseCase {

    Submission create(Caller caller, CreateSubmissionCommand command);

    /** Autosave; {@code expectedVersion} from If-Match, or null to take the latest. */
    Submission saveDraft(
            Caller caller, UUID submissionId, String text, int elapsedSeconds, @Nullable Integer expectedVersion);

    Submission submit(Caller caller, UUID submissionId);

    Submission startRevision(Caller caller, UUID submissionId);

    Evaluation tutorEvaluation(
            Caller caller, UUID submissionId, TutorEvaluationCommand command, @Nullable Integer expectedVersion);
}
