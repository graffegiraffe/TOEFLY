package com.toefly.assessment.application.port.out;

import com.toefly.assessment.application.port.in.SubmissionFilter;
import com.toefly.assessment.domain.evaluation.Evaluation;
import com.toefly.assessment.domain.evaluation.InlineAnnotation;
import com.toefly.assessment.domain.submission.Submission;
import com.toefly.assessment.domain.submission.TaskType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Submissions with their versions and evaluations (assessment_db). */
public interface SubmissionRepository {

    void insert(Submission submission);

    /** Writes the aggregate if its stored version is {@code expectedVersion}, else {@link ConcurrencyConflictException}. */
    void update(Submission submission, int expectedVersion);

    Optional<Submission> find(UUID submissionId);

    List<Submission> list(UUID userId, SubmissionFilter filter, int offset, int limit);

    void saveEvaluation(Evaluation evaluation);

    /** The previous AI evaluation of the version, replaced by a newer one (INV-SUB-08). */
    void deleteAiEvaluation(UUID submissionId, int versionNo);

    List<Evaluation> evaluations(UUID submissionId);

    void updateAnnotations(UUID evaluationId, List<InlineAnnotation> annotations);

    /** Effective scores of first versions of a type for the estimate, newest first. */
    List<Double> firstVersionScores(UUID userId, TaskType type, int limit);
}
