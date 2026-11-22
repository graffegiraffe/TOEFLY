package com.toefly.assessment.adapter.in.web;

import com.toefly.assessment.adapter.in.web.api.InternalAssessmentApi;
import com.toefly.assessment.adapter.in.web.dto.BandEstimate;
import com.toefly.assessment.adapter.in.web.dto.CriterionScore;
import com.toefly.assessment.adapter.in.web.dto.InternalComment;
import com.toefly.assessment.adapter.in.web.dto.InternalEvaluation;
import com.toefly.assessment.adapter.in.web.dto.InternalModelAnswer;
import com.toefly.assessment.adapter.in.web.dto.InternalSubmission;
import com.toefly.assessment.adapter.in.web.dto.InternalSubmissionVersion;
import com.toefly.assessment.adapter.in.web.dto.MistakeCategory;
import com.toefly.assessment.adapter.in.web.dto.Severity;
import com.toefly.assessment.adapter.in.web.dto.TaskType;
import com.toefly.assessment.application.port.in.BandEstimateUseCase;
import com.toefly.assessment.application.port.in.InternalSubmissionView;
import com.toefly.assessment.application.port.in.SubmissionQueryUseCase;
import com.toefly.assessment.domain.evaluation.Evaluation;
import com.toefly.assessment.domain.evaluation.EvaluatorType;
import com.toefly.assessment.domain.evaluation.InlineAnnotation;
import com.toefly.assessment.domain.submission.SubmissionVersion;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /internal/v1}: reads for the tools of tutor-agent-service, with the service token or the user's own JWT
 * (docs/05-api-contracts.md §9.3, docs/06-ai-agent.md §4.5).
 */
@RestController
public class InternalAssessmentController implements InternalAssessmentApi {

    private final SubmissionQueryUseCase submissions;
    private final BandEstimateUseCase estimates;
    private final CurrentCaller caller;

    public InternalAssessmentController(
            SubmissionQueryUseCase submissions, BandEstimateUseCase estimates, CurrentCaller caller) {
        this.submissions = submissions;
        this.estimates = estimates;
        this.caller = caller;
    }

    @Override
    public ResponseEntity<InternalSubmission> getSubmissionInternal(UUID submissionId, @Nullable Integer version) {
        return ResponseEntity.ok(submission(submissions.internal(caller.get(), submissionId, version)));
    }

    @Override
    public ResponseEntity<BandEstimate> getBandEstimateInternal(UUID userId) {
        return ResponseEntity.ok(AssessmentDtoMapper.estimate(estimates.ofUser(userId)));
    }

    static InternalSubmission submission(InternalSubmissionView view) {
        var s = view.submission();
        return new InternalSubmission()
                .submissionId(s.id())
                .userId(s.userId())
                .itemSetId(s.itemSetId())
                .taskType(TaskType.fromValue(s.taskType().name()))
                .weekTaskId(s.weekTaskId())
                .status(AssessmentDtoMapper.status(s.status()))
                .versions(view.versions().stream()
                        .map(v -> version(v, view.evaluations()))
                        .toList())
                .modelAnswer(
                        view.modelAnswer() == null
                                ? null
                                : new InternalModelAnswer()
                                        .text(view.modelAnswer().text())
                                        .source(view.modelAnswer().source()));
    }

    static InternalSubmissionVersion version(SubmissionVersion v, List<Evaluation> evaluations) {
        List<Evaluation> ofVersion =
                evaluations.stream().filter(e -> e.versionNo() == v.versionNo()).toList();
        Evaluation effective = Evaluation.effective(ofVersion);
        Evaluation ai = ofVersion.stream()
                .filter(e -> e.evaluator() == EvaluatorType.AI)
                .findFirst()
                .orElse(null);
        return new InternalSubmissionVersion()
                .versionNo(v.versionNo())
                .status(AssessmentDtoMapper.status(v.status()))
                .text(v.text())
                .wordCount(v.wordCount())
                .submittedAt(v.submittedAt() == null ? null : AssessmentDtoMapper.time(v.submittedAt()))
                .evaluation(effective == null ? null : evaluation(effective, ai))
                .evaluatedAt(effective == null ? null : AssessmentDtoMapper.time(effective.createdAt()))
                .tutorReviewed(effective != null && effective.evaluator() == EvaluatorType.TUTOR);
    }

    /** Score and criteria of the effective evaluation; the inline comments are always those of the AI. */
    private static InternalEvaluation evaluation(Evaluation effective, @Nullable Evaluation ai) {
        List<com.toefly.assessment.domain.evaluation.CriterionScore> criteria =
                effective.criteria().isEmpty() && ai != null ? ai.criteria() : effective.criteria();
        return new InternalEvaluation()
                .finalScore(BigDecimal.valueOf(effective.finalScore()))
                .rubricScore(effective.overallScore())
                .criteria(criteria.stream()
                        .map(c -> new CriterionScore()
                                .criterion(c.criterion())
                                .level(c.level())
                                .note(c.note()))
                        .toList())
                .comments(
                        ai == null
                                ? List.of()
                                : ai.annotations().stream()
                                        .map(InternalAssessmentController::comment)
                                        .toList());
    }

    private static InternalComment comment(InlineAnnotation a) {
        return new InternalComment()
                .quote(a.anchorQuote())
                .comment(a.message())
                .suggestion(a.suggestion())
                .category(MistakeCategory.fromValue(a.category().name()))
                .subcategory(a.subcategory())
                .severity(Severity.fromValue(a.severity().name()));
    }
}
