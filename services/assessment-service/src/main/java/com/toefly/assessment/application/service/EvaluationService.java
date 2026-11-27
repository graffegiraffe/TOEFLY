package com.toefly.assessment.application.service;

import com.toefly.assessment.application.port.in.EvaluateSubmissionUseCase;
import com.toefly.assessment.application.port.out.AiUnavailableException;
import com.toefly.assessment.application.port.out.AssessmentEventPublisherPort;
import com.toefly.assessment.application.port.out.Cause;
import com.toefly.assessment.application.port.out.DeferredEvaluationRepository;
import com.toefly.assessment.application.port.out.DependencyUnavailableException;
import com.toefly.assessment.application.port.out.SubmissionRepository;
import com.toefly.assessment.application.port.out.TaskContent;
import com.toefly.assessment.application.port.out.TaskContentPort;
import com.toefly.assessment.domain.evaluation.Evaluation;
import com.toefly.assessment.domain.evaluation.EvaluatorType;
import com.toefly.assessment.domain.evaluation.InlineAnnotation;
import com.toefly.assessment.domain.evaluation.Severity;
import com.toefly.assessment.domain.grading.AnnotationCarryOver;
import com.toefly.assessment.domain.submission.DeferredEvaluation;
import com.toefly.assessment.domain.submission.ModelAnswer;
import com.toefly.assessment.domain.submission.Submission;
import com.toefly.assessment.domain.submission.SubmissionVersion;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Worker of {@code evaluation-requested} (docs/06-ai-agent.md §9.2): grades a version outside any transaction, then
 * stores the evaluation, carries the statuses of the previous comments, publishes {@code submission-evaluated} and
 * recomputes the estimate in one transaction. A failure of AI defers the version instead (§8.3).
 */
@Service
class EvaluationService implements EvaluateSubmissionUseCase {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final SubmissionRepository submissions;
    private final DeferredEvaluationRepository deferred;
    private final TaskContentPort content;
    private final WritingGrader grader;
    private final AssessmentEventPublisherPort events;
    private final BandEstimateService estimates;
    private final Clock clock;
    private final MeterRegistry meters;

    EvaluationService(
            SubmissionRepository submissions,
            DeferredEvaluationRepository deferred,
            TaskContentPort content,
            WritingGrader grader,
            AssessmentEventPublisherPort events,
            BandEstimateService estimates,
            Clock clock,
            MeterRegistry meters) {
        this.submissions = submissions;
        this.deferred = deferred;
        this.content = content;
        this.grader = grader;
        this.events = events;
        this.estimates = estimates;
        this.clock = clock;
        this.meters = meters;
    }

    @Override
    public void evaluate(UUID submissionId, int versionNo, Cause cause, Predicate<Runnable> commitOnce) {
        Optional<Submission> found = submissions.find(submissionId);
        if (found.isEmpty() || !found.get().awaitsEvaluation(versionNo)) {
            commitOnce.test(() -> {});
            return;
        }
        Submission submission = found.get();
        GradingResult result;
        ModelAnswer model;
        try {
            TaskContent task = content.load(submission.itemSetId())
                    .orElseThrow(() -> new AiUnavailableException("item set is gone", null, true));
            model = task.model();
            result = grader.grade(input(submission, versionNo, task));
        } catch (AiUnavailableException | DependencyUnavailableException failure) {
            Duration retryAfter = failure instanceof AiUnavailableException ai ? ai.retryAfter() : null;
            log.warn("Evaluation of {} v{} deferred: {}", submissionId, versionNo, failure.getMessage());
            meters.counter("toefly.assessment.evaluation.deferred").increment();
            commitOnce.test(() -> defer(submissionId, versionNo, retryAfter, failure.getMessage()));
            return;
        }
        commitOnce.test(() -> complete(submissionId, versionNo, result, model, cause));
    }

    private GradingInput input(Submission submission, int versionNo, TaskContent task) {
        SubmissionVersion version = submission.version(versionNo);
        GradingInput.Previous previous = null;
        SubmissionVersion before = submission.previous(versionNo);
        if (before != null) {
            Evaluation evaluation = Evaluation.effective(ofVersion(submission.id(), before.versionNo()));
            if (evaluation != null) {
                previous =
                        new GradingInput.Previous(before.versionNo(), evaluation.finalScore(), mainIssues(evaluation));
            }
        }
        return new GradingInput(
                submission.id(),
                versionNo,
                submission.userId(),
                task,
                version.text(),
                version.wordCount(),
                version.activeSeconds() + version.overtimeSeconds(),
                previous);
    }

    private void complete(
            UUID submissionId, int versionNo, GradingResult result, @Nullable ModelAnswer model, Cause cause) {
        Submission submission = submissions.find(submissionId).orElseThrow();
        if (!submission.awaitsEvaluation(versionNo)) {
            return;
        }
        if (model != null) {
            // the task may have changed since the submission was created; the review shows the current one
            submissions.saveModelAnswer(submissionId, model);
        }
        int before = submission.version();
        Instant now = clock.instant();
        Evaluation evaluation = result.toEvaluation(
                submissionId, versionNo, submission.taskType().rubricId(), now);
        submissions.deleteAiEvaluation(submissionId, versionNo);
        submissions.saveEvaluation(evaluation);
        SubmissionVersion previous = submission.previous(versionNo);
        if (previous != null) {
            Evaluation old = ofVersion(submissionId, previous.versionNo()).stream()
                    .filter(e -> e.evaluator() == EvaluatorType.AI)
                    .findFirst()
                    .orElse(null);
            if (old != null) {
                List<InlineAnnotation> carried = AnnotationCarryOver.carry(
                        previous.text(),
                        submission.version(versionNo).text(),
                        old.annotations(),
                        evaluation.annotations());
                submissions.updateAnnotations(old.id(), carried);
            }
        }
        submission.evaluated(versionNo);
        submissions.update(submission, before);
        deferred.delete(submissionId, versionNo);
        events.submissionEvaluated(submission, evaluation, cause);
        SubmissionVersion version = submission.version(versionNo);
        if (version.submittedAt() != null) {
            Timer.builder("toefly.assessment.evaluation.duration")
                    .description("From submission to EVALUATED")
                    .tag("taskType", submission.taskType().name())
                    .publishPercentiles(0.95, 0.99)
                    .register(meters)
                    .record(Duration.between(version.submittedAt(), now));
        }
        if (version.countsForEstimate()) {
            estimates.recompute(submission.userId(), cause);
        }
    }

    private void defer(UUID submissionId, int versionNo, @Nullable Duration retryAfter, @Nullable String error) {
        Submission submission = submissions.find(submissionId).orElseThrow();
        if (!submission.awaitsEvaluation(versionNo)) {
            return;
        }
        int before = submission.version();
        Instant now = clock.instant();
        String message = error == null ? "AI unavailable" : error.length() > 500 ? error.substring(0, 500) : error;
        DeferredEvaluation row = deferred.find(submissionId, versionNo)
                .map(existing -> existing.failedAgain(now, retryAfter, message))
                .orElseGet(() -> DeferredEvaluation.first(
                        submissionId, versionNo, submission.userId(), now, retryAfter, message));
        submission.defer(versionNo);
        submissions.update(submission, before);
        deferred.save(row);
    }

    private List<Evaluation> ofVersion(UUID submissionId, int versionNo) {
        return submissions.evaluations(submissionId).stream()
                .filter(e -> e.versionNo() == versionNo)
                .toList();
    }

    /** Subcategories of the strongest comments, for the previous-version block of the prompt. */
    static List<String> mainIssues(Evaluation evaluation) {
        return evaluation.annotations().stream()
                .sorted(Comparator.comparing((InlineAnnotation a) -> a.severity() != Severity.CRITICAL)
                        .thenComparing(a -> a.severity() != Severity.MAJOR))
                .map(InlineAnnotation::subcategory)
                .distinct()
                .limit(5)
                .toList();
    }
}
