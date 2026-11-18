package com.toefly.assessment.application.service;

import static com.toefly.assessment.application.service.Fakes.ONCE;
import static com.toefly.assessment.application.service.Fakes.RESPONSE;
import static com.toefly.assessment.application.service.Fakes.STUDENT;
import static com.toefly.assessment.application.service.Fakes.STUDENT_CALLER;
import static com.toefly.assessment.application.service.Fakes.TUTOR;
import static com.toefly.assessment.application.service.Fakes.TUTOR_CALLER;
import static com.toefly.assessment.application.service.Fakes.pass;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.assessment.application.port.in.TutorEvaluationCommand;
import com.toefly.assessment.application.port.out.Cause;
import com.toefly.assessment.application.port.out.SubmissionRepository;
import com.toefly.assessment.domain.estimate.BandEstimate;
import com.toefly.assessment.domain.estimate.Section;
import com.toefly.assessment.domain.evaluation.Evaluation;
import com.toefly.assessment.domain.evaluation.EvaluatorType;
import com.toefly.assessment.domain.submission.Submission;
import com.toefly.assessment.domain.submission.SubmissionStatus;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("tutor scores and the band estimate")
class TutorAndEstimateScenarioTest {

    private final Fakes app = new Fakes();

    private Submission evaluated(int a, int b) {
        Submission submission = app.submittedDiscussion(RESPONSE);
        app.ai.answer("A", pass("A", a));
        app.ai.answer("B", pass("B", b));
        app.worker.evaluate(submission.id(), 1, Cause.none(), ONCE);
        return submission;
    }

    @Test
    @DisplayName("AC-11.09.1 a linked tutor overrides the score; the version becomes TUTOR_REVIEWED")
    void tutorOverrides() {
        Submission submission = evaluated(3, 3);
        assertThatThrownBy(() -> app.commands.tutorEvaluation(
                        TUTOR_CALLER, submission.id(), new TutorEvaluationCommand(1, 4.5, Map.of(), ""), null))
                .extracting("code")
                .isEqualTo("not-found");
        app.tutorLinks.enrolled(TUTOR, STUDENT);
        Evaluation tutor = app.commands.tutorEvaluation(
                TUTOR_CALLER,
                submission.id(),
                new TutorEvaluationCommand(1, 4.5, Map.of("development", 4), "Good point about schools."),
                submission.version());
        assertThat(tutor.evaluator()).isEqualTo(EvaluatorType.TUTOR);
        assertThat(submission.status()).isEqualTo(SubmissionStatus.TUTOR_REVIEWED);
        assertThat(Evaluation.effective(app.submissions.evaluations(submission.id())))
                .isEqualTo(tutor);
        assertThat(app.events.types()).last().isEqualTo("submission-evaluated");
        assertThat(app.queries.get(TUTOR_CALLER, submission.id()).evaluations()).hasSize(2);
        assertThat(app.queries.list(TUTOR_CALLER, STUDENT, new SubmissionRepository.Filter(null, null, null), 0, 10))
                .hasSize(1);
        app.tutorLinks.unenrolled(TUTOR, STUDENT);
        assertThatThrownBy(() -> app.queries.get(TUTOR_CALLER, submission.id())).hasMessageContaining("No such");
    }

    @Test
    @DisplayName("AC-11.09.2 the estimate uses the tutor score of a first version")
    void estimateUsesTutorScore() {
        Submission first = evaluated(2, 2);
        evaluated(3, 3);
        app.tutorLinks.enrolled(TUTOR, STUDENT);
        app.commands.tutorEvaluation(TUTOR_CALLER, first.id(), new TutorEvaluationCommand(1, 5.0, Map.of(), ""), null);
        BandEstimate estimate = app.estimateService.current(STUDENT_CALLER, null);
        assertThat(app.submissions.firstVersionScores(
                        STUDENT, com.toefly.assessment.domain.submission.TaskType.WRITE_ACADEMIC_DISCUSSION, 5))
                .containsExactlyInAnyOrder(5.0, 3.0);
        assertThat(estimate.section(Section.WRITING).ready()).isTrue();
    }

    @Test
    @DisplayName("AC-13.01.2 ability and Build a Sentence results recompute the estimate and publish changes only")
    void recomputesOnSignals() {
        app.estimateService.onboardingCompleted(STUDENT, app.clock.now.minus(Duration.ofDays(10)), Cause.none());
        int published = app.events.estimates.size();
        app.estimateService.abilityUpdated(STUDENT, Section.READING, 1.3, 0.45, 40, app.clock.now, Cause.none());
        assertThat(app.events.estimates).hasSize(published + 1);
        assertThat(app.estimateService
                        .current(STUDENT_CALLER, null)
                        .section(Section.READING)
                        .ready())
                .isTrue();
        app.estimateService.abilityUpdated(STUDENT, Section.READING, 1.3, 0.45, 40, app.clock.now, Cause.none());
        assertThat(app.events.estimates).hasSize(published + 1);
        app.estimateService.buildSentenceScored(
                STUDENT, UUID.randomUUID(), Collections.nCopies(10, true), app.clock.now, Cause.none());
        assertThat(app.estimates.bas.get(STUDENT)).hasSize(10);
        assertThat(app.estimateService.history(STUDENT_CALLER, null)).hasSize(1);
        app.estimateService.recomputeNow(STUDENT);
        assertThatThrownBy(() -> app.estimateService.current(Fakes.STRANGER_CALLER, STUDENT))
                .hasMessageContaining("No such student");
        assertThat(app.estimateService.current(Fakes.ADMIN_CALLER, STUDENT).sections())
                .hasSize(4);
    }

    @Test
    @DisplayName("AC-13.01.3 without a stored estimate the current one is computed and says need more data")
    void computesWithoutStored() {
        BandEstimate estimate = app.estimateService.current(STUDENT_CALLER, null);
        assertThat(estimate.overall()).isNull();
        assertThat(estimate.sections()).noneMatch(s -> s.ready());
        assertThat(app.events.estimates).isEmpty();
        List<Integer> remaining =
                estimate.sections().stream().map(s -> s.remaining()).toList();
        assertThat(remaining).containsExactly(20, 20, 2, 2);
    }
}
