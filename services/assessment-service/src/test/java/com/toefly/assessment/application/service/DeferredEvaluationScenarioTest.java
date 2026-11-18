package com.toefly.assessment.application.service;

import static com.toefly.assessment.application.service.Fakes.ONCE;
import static com.toefly.assessment.application.service.Fakes.RESPONSE;
import static com.toefly.assessment.application.service.Fakes.STUDENT_CALLER;
import static com.toefly.assessment.application.service.Fakes.pass;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.assessment.application.port.out.Cause;
import com.toefly.assessment.domain.submission.DeferredEvaluation;
import com.toefly.assessment.domain.submission.Submission;
import com.toefly.assessment.domain.submission.SubmissionStatus;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EVALUATION_DEFERRED: the response is kept and checked again automatically")
class DeferredEvaluationScenarioTest {

    private final Fakes app = new Fakes();

    @Test
    @DisplayName("AC-11.06.1 AI unavailable defers the version and keeps the response")
    void defers() {
        Submission submission = app.submittedDiscussion(RESPONSE);
        app.ai.unavailable(Duration.ofSeconds(30));
        app.worker.evaluate(submission.id(), 1, Cause.none(), ONCE);
        assertThat(submission.status()).isEqualTo(SubmissionStatus.EVALUATION_DEFERRED);
        DeferredEvaluation row = app.deferred.find(submission.id(), 1).orElseThrow();
        assertThat(row.nextAttemptAt()).isEqualTo(app.clock.now.plus(Duration.ofMinutes(1)));
        assertThat(row.lastError()).contains("429");
        assertThat(app.submissions.evaluations(submission.id())).isEmpty();
        assertThat(app.queries.get(STUDENT_CALLER, submission.id()).deferred()).isNotNull();
        assertThat(app.meters.counter("toefly.assessment.evaluation.deferred").count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("AC-11.06.2 the job retries when due and the next success evaluates the version")
    void retriesAndSucceeds() {
        Submission submission = app.submittedDiscussion(RESPONSE);
        app.ai.unavailable(null);
        app.worker.evaluate(submission.id(), 1, Cause.none(), ONCE);
        assertThat(app.retries.retryDue()).isZero();

        app.clock.advance(Duration.ofMinutes(1));
        assertThat(app.retries.retryDue()).isEqualTo(1);
        assertThat(submission.status()).isEqualTo(SubmissionStatus.EVALUATING);
        assertThat(app.events.published.getLast().attemptNo()).isEqualTo(2);
        assertThat(app.retries.retryDue()).isZero();

        app.ai.answer("A", pass("A", 4));
        app.ai.answer("B", pass("B", 4));
        app.worker.evaluate(submission.id(), 1, Cause.none(), ONCE);
        assertThat(submission.status()).isEqualTo(SubmissionStatus.EVALUATED);
        assertThat(app.deferred.rows).isEmpty();
    }

    @Test
    @DisplayName("AC-11.06.2 a failed retry waits for the next step of the back-off")
    void failsAgain() {
        Submission submission = app.submittedDiscussion(RESPONSE);
        app.ai.unavailable(null);
        app.worker.evaluate(submission.id(), 1, Cause.none(), ONCE);
        app.clock.advance(Duration.ofMinutes(1));
        app.retries.retryDue();
        app.content.down = true;
        app.worker.evaluate(submission.id(), 1, Cause.none(), ONCE);
        DeferredEvaluation row = app.deferred.find(submission.id(), 1).orElseThrow();
        assertThat(row.attempts()).isEqualTo(2);
        assertThat(row.nextAttemptAt()).isEqualTo(app.clock.now.plus(Duration.ofMinutes(2)));
        assertThat(submission.status()).isEqualTo(SubmissionStatus.EVALUATION_DEFERRED);
    }

    @Test
    @DisplayName("AC-11.06.4 after 24 hours the row is dead-lettered once with an alert and retries go on")
    void deadLetters() {
        Submission submission = app.submittedDiscussion(RESPONSE);
        app.ai.unavailable(null);
        app.worker.evaluate(submission.id(), 1, Cause.none(), ONCE);
        app.clock.advance(Duration.ofHours(25));
        app.retries.retryDue();
        assertThat(app.deferred.find(submission.id(), 1).orElseThrow().deadLettered())
                .isTrue();
        assertThat(app.meters
                        .counter("toefly.assessment.evaluation.dead-lettered")
                        .count())
                .isEqualTo(1);
        app.ai.unavailable(null);
        app.worker.evaluate(submission.id(), 1, Cause.none(), ONCE);
        app.clock.advance(Duration.ofHours(2));
        app.retries.retryDue();
        assertThat(app.meters
                        .counter("toefly.assessment.evaluation.dead-lettered")
                        .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("AC-11.06.4 an admin sees the queue and retries now; stale rows are dropped")
    void adminRetry() {
        Submission submission = app.submittedDiscussion(RESPONSE);
        app.ai.unavailable(null);
        app.worker.evaluate(submission.id(), 1, Cause.none(), ONCE);
        assertThat(app.retries.page(0, 10)).hasSize(1);
        app.retries.retryNow(submission.id());
        assertThat(submission.status()).isEqualTo(SubmissionStatus.EVALUATING);
        assertThatThrownBy(() -> app.retries.retryNow(UUID.randomUUID())).hasMessageContaining("No such");

        app.ai.answer("A", pass("A", 3));
        app.ai.answer("B", pass("B", 3));
        app.worker.evaluate(submission.id(), 1, Cause.none(), ONCE);
        assertThatThrownBy(() -> app.retries.retryNow(submission.id())).hasMessageContaining("not deferred");
        app.deferred.save(DeferredEvaluation.first(
                submission.id(), 1, submission.userId(), app.clock.now.minus(Duration.ofHours(1)), null, "old"));
        app.clock.advance(Duration.ofHours(1));
        assertThat(app.retries.retryDue()).isZero();
        assertThat(app.deferred.rows).isEmpty();
    }
}
