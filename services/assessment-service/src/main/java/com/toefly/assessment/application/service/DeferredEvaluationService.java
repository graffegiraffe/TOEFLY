package com.toefly.assessment.application.service;

import com.toefly.assessment.application.port.in.DeferredEvaluationUseCase;
import com.toefly.assessment.application.port.out.AssessmentEventPublisherPort;
import com.toefly.assessment.application.port.out.Cause;
import com.toefly.assessment.application.port.out.DeferredEvaluationRepository;
import com.toefly.assessment.application.port.out.SubmissionRepository;
import com.toefly.assessment.domain.submission.DeferredEvaluation;
import com.toefly.assessment.domain.submission.Submission;
import com.toefly.assessment.domain.submission.SubmissionRuleException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * {@code DeferredEvaluationJob}: sends due retries of deferred versions as a new {@code evaluation-requested}; after
 * 24 hours the row is marked dead-lettered once with an alert, and the hourly retries go on (docs/12 phase 5b
 * decision 6).
 */
@Service
class DeferredEvaluationService implements DeferredEvaluationUseCase {

    static final int BATCH = 20;
    private static final Logger log = LoggerFactory.getLogger(DeferredEvaluationService.class);

    private final DeferredEvaluationRepository deferred;
    private final SubmissionRepository submissions;
    private final AssessmentEventPublisherPort events;
    private final Transactions transactions;
    private final Clock clock;
    private final MeterRegistry meters;

    DeferredEvaluationService(
            DeferredEvaluationRepository deferred,
            SubmissionRepository submissions,
            AssessmentEventPublisherPort events,
            Transactions transactions,
            Clock clock,
            MeterRegistry meters) {
        this.deferred = deferred;
        this.submissions = submissions;
        this.events = events;
        this.transactions = transactions;
        this.clock = clock;
        this.meters = meters;
    }

    @Override
    public int retryDue() {
        return transactions.once(() -> {
            Instant now = clock.instant();
            int sent = 0;
            for (DeferredEvaluation row : deferred.lockDue(now, BATCH)) {
                if (retry(row, now)) {
                    sent++;
                }
            }
            return sent;
        });
    }

    @Override
    public List<DeferredEvaluation> page(int offset, int limit) {
        return deferred.page(offset, limit);
    }

    @Override
    public void retryNow(UUID submissionId) {
        transactions.once(() -> {
            Submission submission = submissions
                    .find(submissionId)
                    .orElseThrow(() -> new SubmissionRuleException("not-found", "No such submission."));
            DeferredEvaluation row = deferred.find(
                            submissionId, submission.current().versionNo())
                    .orElseThrow(() -> new SubmissionRuleException("not-found", "The submission is not deferred."));
            retry(row, clock.instant());
            return row;
        });
    }

    private boolean retry(DeferredEvaluation row, Instant now) {
        Submission submission = submissions.find(row.submissionId()).orElse(null);
        if (submission == null || !submission.awaitsEvaluation(row.versionNo())) {
            deferred.delete(row.submissionId(), row.versionNo());
            return false;
        }
        DeferredEvaluation next = row;
        if (row.shouldDeadLetter(now)) {
            log.error(
                    "Evaluation of submission {} v{} is deferred for 24 hours: {}",
                    row.submissionId(),
                    row.versionNo(),
                    row.lastError());
            meters.counter("toefly.assessment.evaluation.dead-lettered").increment();
            next = next.markDeadLettered();
        }
        int before = submission.version();
        submission.startEvaluation(row.versionNo());
        submissions.update(submission, before);
        next = next.retried(now);
        deferred.save(next);
        events.evaluationRequested(submission, row.versionNo(), next.attempts(), Cause.none());
        return true;
    }
}
