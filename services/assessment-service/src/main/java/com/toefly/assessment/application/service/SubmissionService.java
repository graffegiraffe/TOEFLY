package com.toefly.assessment.application.service;

import com.toefly.assessment.application.port.in.Caller;
import com.toefly.assessment.application.port.in.CreateSubmissionCommand;
import com.toefly.assessment.application.port.in.SubmissionCommandUseCase;
import com.toefly.assessment.application.port.in.TutorEvaluationCommand;
import com.toefly.assessment.application.port.in.VersionMismatchException;
import com.toefly.assessment.application.port.out.AssessmentEventPublisherPort;
import com.toefly.assessment.application.port.out.Cause;
import com.toefly.assessment.application.port.out.SubmissionRepository;
import com.toefly.assessment.application.port.out.TaskContent;
import com.toefly.assessment.application.port.out.TaskContentPort;
import com.toefly.assessment.domain.evaluation.CriterionScore;
import com.toefly.assessment.domain.evaluation.Evaluation;
import com.toefly.assessment.domain.evaluation.EvaluatorType;
import com.toefly.assessment.domain.submission.ModelAnswer;
import com.toefly.assessment.domain.submission.Submission;
import com.toefly.assessment.domain.submission.SubmissionRuleException;
import com.toefly.assessment.domain.submission.SubmissionVersion;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** The Writing loop: create, autosave, submit, revise, tutor score (docs/05-api-contracts.md §6.5). */
@Service
class SubmissionService implements SubmissionCommandUseCase {

    private final SubmissionRepository submissions;
    private final TaskContentPort content;
    private final AssessmentEventPublisherPort events;
    private final BandEstimateService estimates;
    private final Access access;
    private final Transactions transactions;
    private final Clock clock;

    SubmissionService(
            SubmissionRepository submissions,
            TaskContentPort content,
            AssessmentEventPublisherPort events,
            BandEstimateService estimates,
            Access access,
            Transactions transactions,
            Clock clock) {
        this.submissions = submissions;
        this.content = content;
        this.events = events;
        this.estimates = estimates;
        this.access = access;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Submission create(Caller caller, CreateSubmissionCommand command) {
        TaskContent task = content.load(command.itemSetId())
                .orElseThrow(() ->
                        new SubmissionRuleException("item-set-unavailable", "This task is not available any more."));
        Submission submission = Submission.start(
                caller.userId(),
                task.itemSetId(),
                task.itemSetVersion(),
                task.taskType(),
                command.mode(),
                command.weekTaskId(),
                command.examSessionId(),
                clock.instant());
        ModelAnswer model = task.model();
        transactions.once(() -> {
            submissions.insert(submission);
            if (model != null) {
                submissions.saveModelAnswer(submission.id(), model);
            }
            return submission;
        });
        return submission;
    }

    @Override
    public Submission saveDraft(
            Caller caller, UUID submissionId, String text, int elapsedSeconds, @Nullable Integer expectedVersion) {
        return change(caller, submissionId, expectedVersion, submission -> submission.saveDraft(text, elapsedSeconds));
    }

    /** SUBMITTED and at once EVALUATING with the command in the outbox (rights are not checked yet, decision 7). */
    @Override
    public Submission submit(Caller caller, UUID submissionId) {
        return change(caller, submissionId, null, submission -> {
            SubmissionVersion version = submission.submit(clock.instant());
            submission.startEvaluation(version.versionNo());
            events.evaluationRequested(submission, version.versionNo(), 1, Cause.none());
        });
    }

    @Override
    public Submission startRevision(Caller caller, UUID submissionId) {
        return change(caller, submissionId, null, Submission::startRevision);
    }

    @Override
    public Evaluation tutorEvaluation(
            Caller caller, UUID submissionId, TutorEvaluationCommand command, @Nullable Integer expectedVersion) {
        return transactions.once(() -> {
            Submission submission = load(submissionId);
            access.tutor(caller, submission.userId());
            check(submission, expectedVersion);
            int before = submission.version();
            SubmissionVersion version = submission.version(command.versionNo());
            Instant now = clock.instant();
            List<CriterionScore> criteria = command.criteria().entrySet().stream()
                    .map(e -> new CriterionScore(e.getKey(), e.getValue(), ""))
                    .toList();
            Evaluation evaluation = new Evaluation(
                    UUID.randomUUID(),
                    submission.id(),
                    version.versionNo(),
                    EvaluatorType.TUTOR,
                    caller.userId(),
                    submission.taskType().rubricId(),
                    command.score(),
                    criteria,
                    List.of(),
                    command.note().isBlank() ? List.of() : List.of(command.note()),
                    List.of(),
                    List.of(),
                    null,
                    false,
                    List.of(),
                    List.of(),
                    null,
                    0,
                    null,
                    false,
                    now);
            submissions.saveEvaluation(evaluation);
            submission.tutorReviewed(version.versionNo());
            submissions.update(submission, before);
            Cause cause = Cause.none();
            events.submissionEvaluated(submission, evaluation, cause);
            if (version.countsForEstimate()) {
                estimates.recompute(submission.userId(), cause);
            }
            return evaluation;
        });
    }

    private Submission change(
            Caller caller, UUID submissionId, @Nullable Integer expectedVersion, Consumer<Submission> work) {
        return transactions.once(() -> {
            Submission submission = load(submissionId);
            access.own(caller, submission.userId());
            check(submission, expectedVersion);
            int before = submission.version();
            work.accept(submission);
            submissions.update(submission, before);
            return submission;
        });
    }

    private Submission load(UUID submissionId) {
        return submissions
                .find(submissionId)
                .orElseThrow(() -> new SubmissionRuleException("not-found", "No such submission."));
    }

    private static void check(Submission submission, @Nullable Integer expectedVersion) {
        if (expectedVersion != null && expectedVersion != submission.version()) {
            throw new VersionMismatchException(submission.version());
        }
    }
}
