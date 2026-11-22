package com.toefly.assessment.application.service;

import static com.toefly.assessment.application.service.Fakes.ONCE;
import static com.toefly.assessment.application.service.Fakes.RESPONSE;
import static com.toefly.assessment.application.service.Fakes.STRANGER_CALLER;
import static com.toefly.assessment.application.service.Fakes.STUDENT;
import static com.toefly.assessment.application.service.Fakes.STUDENT_CALLER;
import static com.toefly.assessment.application.service.Fakes.TUTOR;
import static com.toefly.assessment.application.service.Fakes.TUTOR_CALLER;
import static com.toefly.assessment.application.service.Fakes.pass;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.assessment.application.port.in.Caller;
import com.toefly.assessment.application.port.in.InternalSubmissionView;
import com.toefly.assessment.application.port.in.TutorEvaluationCommand;
import com.toefly.assessment.application.port.out.Cause;
import com.toefly.assessment.domain.estimate.Section;
import com.toefly.assessment.domain.submission.ModelAnswer;
import com.toefly.assessment.domain.submission.Submission;
import com.toefly.assessment.domain.submission.SubmissionStatus;
import com.toefly.assessment.domain.submission.SubmissionVersion;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("internal reads for tutor-agent-service (docs/12 phase 5c, decision 2)")
class InternalReadsScenarioTest {

    private static final Caller SERVICE = new Caller(UUID.randomUUID(), Set.of(Caller.Role.SERVICE));

    private final Fakes app = new Fakes();

    private Submission evaluated() {
        Submission submission = app.submittedDiscussion(RESPONSE);
        app.ai.answer("A", pass("A", 3, Fakes.lives()));
        app.ai.answer("B", pass("B", 3, Fakes.lives()));
        app.worker.evaluate(submission.id(), 1, Cause.none(), ONCE);
        return submission;
    }

    @Test
    @DisplayName("before the evaluation the submission comes without the model answer")
    void noModelAnswerBeforeEvaluation() {
        Submission submission = app.submittedDiscussion(RESPONSE);

        InternalSubmissionView view = app.queries.internal(SERVICE, submission.id(), null);

        assertThat(view.versions()).extracting(SubmissionVersion::status).containsExactly(SubmissionStatus.EVALUATING);
        assertThat(view.evaluations()).isEmpty();
        assertThat(view.modelAnswer()).isNull();
        assertThat(app.submissions.modelAnswer(submission.id())).contains(new ModelAnswer("Model answer", "BOOK"));
    }

    @Test
    @DisplayName("an evaluated version comes with its evaluation and the model answer")
    void evaluatedVersionHasTheModelAnswer() {
        Submission submission = evaluated();

        InternalSubmissionView view = app.queries.internal(STUDENT_CALLER, submission.id(), null);

        assertThat(view.submission().status()).isEqualTo(SubmissionStatus.EVALUATED);
        assertThat(view.evaluations()).hasSize(1);
        assertThat(view.evaluations().getFirst().annotations()).isNotEmpty();
        assertThat(view.modelAnswer()).isEqualTo(new ModelAnswer("Model answer", "BOOK"));
    }

    @Test
    @DisplayName("the evaluation keeps the model answer of the task it was graded against")
    void evaluationRefreshesTheModelAnswer() {
        Submission submission = app.submittedDiscussion(RESPONSE);
        app.submissions.modelAnswers.clear();
        app.ai.answer("A", pass("A", 3));
        app.ai.answer("B", pass("B", 3));
        app.worker.evaluate(submission.id(), 1, Cause.none(), ONCE);

        assertThat(app.queries.internal(SERVICE, submission.id(), null).modelAnswer())
                .isNotNull();
    }

    @Test
    @DisplayName("a user token reads only its own submission; anything else is not-found")
    void onlyTheOwnerOrAService() {
        Submission submission = evaluated();
        app.tutorLinks.enrolled(TUTOR, STUDENT);

        assertThatThrownBy(() -> app.queries.internal(STRANGER_CALLER, submission.id(), null))
                .extracting("code")
                .isEqualTo("not-found");
        assertThatThrownBy(() -> app.queries.internal(TUTOR_CALLER, submission.id(), null))
                .extracting("code")
                .isEqualTo("not-found");
        assertThatThrownBy(() -> app.queries.internal(SERVICE, UUID.randomUUID(), null))
                .extracting("code")
                .isEqualTo("not-found");
    }

    @Test
    @DisplayName("a draft of Attempt 2 hides the model answer; version 1 alone shows it")
    void versionSelectsWhatIsShown() {
        Submission submission = evaluated();
        app.commands.startRevision(STUDENT_CALLER, submission.id());

        InternalSubmissionView latest = app.queries.internal(SERVICE, submission.id(), null);
        assertThat(latest.versions()).hasSize(2);
        assertThat(latest.modelAnswer()).isNull();

        InternalSubmissionView first = app.queries.internal(SERVICE, submission.id(), 1);
        assertThat(first.versions()).extracting(SubmissionVersion::versionNo).containsExactly(1);
        assertThat(first.evaluations()).allMatch(e -> e.versionNo() == 1);
        assertThat(first.modelAnswer()).isNotNull();

        assertThatThrownBy(() -> app.queries.internal(SERVICE, submission.id(), 3))
                .extracting("code")
                .isEqualTo("not-found");
    }

    @Test
    @DisplayName("a version scored by the tutor shows the model answer too")
    void tutorReviewedShowsTheModelAnswer() {
        Submission submission = evaluated();
        app.tutorLinks.enrolled(TUTOR, STUDENT);
        app.commands.tutorEvaluation(
                TUTOR_CALLER, submission.id(), new TutorEvaluationCommand(1, 4.5, Map.of(), ""), null);

        InternalSubmissionView view = app.queries.internal(SERVICE, submission.id(), null);

        assertThat(view.versions().getFirst().status()).isEqualTo(SubmissionStatus.TUTOR_REVIEWED);
        assertThat(view.evaluations()).hasSize(2);
        assertThat(view.modelAnswer()).isNotNull();
    }

    @Test
    @DisplayName("a model answer has a text; a task without one keeps none")
    void modelAnswerNeedsText() {
        assertThatThrownBy(() -> new ModelAnswer(" ", null)).isInstanceOf(IllegalArgumentException.class);
        Submission email = app.commands.create(
                STUDENT_CALLER,
                new com.toefly.assessment.application.port.in.CreateSubmissionCommand(
                        Fakes.EMAIL_SET, null, null, com.toefly.assessment.domain.submission.AttemptMode.PRACTICE));
        assertThat(app.submissions.modelAnswer(email.id())).isEmpty();
    }

    @Test
    @DisplayName("the internal band estimate is the estimate the learner sees")
    void bandEstimateOfUser() {
        evaluated();

        assertThat(app.estimateService.ofUser(STUDENT)).isEqualTo(app.estimateService.current(STUDENT_CALLER, null));
        assertThat(app.estimateService.ofUser(STUDENT).section(Section.WRITING)).isNotNull();
    }
}
