package com.toefly.assessment.domain.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubmissionTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private static Submission practice() {
        return Submission.start(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                TaskType.WRITE_ACADEMIC_DISCUSSION,
                AttemptMode.PRACTICE,
                null,
                null,
                NOW);
    }

    private static Submission evaluatedDraft() {
        Submission submission = practice();
        submission.saveDraft("People live longer because medicine is better.", 300);
        submission.submit(NOW);
        submission.startEvaluation(1);
        submission.evaluated(1);
        return submission;
    }

    @Test
    @DisplayName("AC-09.03.4 the first submission is version 1, SUBMITTED, then EVALUATING")
    void submitsTheDraft() {
        Submission submission = practice();
        submission.saveDraft("A short\r\nanswer", 120);
        SubmissionVersion version = submission.submit(NOW);
        assertThat(version.versionNo()).isEqualTo(1);
        assertThat(version.status()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(version.text()).isEqualTo("A short\nanswer");
        assertThat(version.wordCount()).isEqualTo(3);
        assertThat(version.countsForEstimate()).isTrue();
        submission.startEvaluation(1);
        assertThat(submission.status()).isEqualTo(SubmissionStatus.EVALUATING);
        assertThat(submission.version()).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-09.02.2 time over the limit of a practice task is overtime")
    void countsOvertime() {
        Submission submission = practice();
        SubmissionVersion version = submission.saveDraft("Words", 700);
        assertThat(version.activeSeconds()).isEqualTo(600);
        assertThat(version.overtimeSeconds()).isEqualTo(100);
    }

    @Test
    @DisplayName("INV-SUB-06 an empty practice response is not submitted")
    void rejectsEmptyPractice() {
        assertThatThrownBy(() -> practice().submit(NOW))
                .isInstanceOf(SubmissionRuleException.class)
                .extracting("code")
                .isEqualTo("submission-not-ready");
    }

    @Test
    @DisplayName("INV-SUB-06 an empty exam response is submitted")
    void acceptsEmptyExam() {
        Submission exam = Submission.start(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                TaskType.WRITE_EMAIL,
                AttemptMode.EXAM,
                null,
                UUID.randomUUID(),
                NOW);
        assertThat(exam.submit(NOW).status()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThatThrownBy(exam::startRevision).hasMessageContaining("Exam");
    }

    @Test
    @DisplayName("AC-11.05.1 Attempt 2 starts from the text of the evaluated draft and does not count")
    void startsAttemptTwo() {
        Submission submission = evaluatedDraft();
        SubmissionVersion attempt2 = submission.startRevision();
        assertThat(attempt2.versionNo()).isEqualTo(2);
        assertThat(attempt2.text()).isEqualTo("People live longer because medicine is better.");
        assertThat(attempt2.countsForEstimate()).isFalse();
        assertThat(submission.status()).isEqualTo(SubmissionStatus.DRAFT);
        assertThat(submission.saveDraft("New text", 9999).activeSeconds()).isEqualTo(9999);
        assertThat(submission.previous(2)).isNotNull();
    }

    @Test
    @DisplayName("INV-SUB-02 a revision needs an evaluated version")
    void refusesRevisionBeforeEvaluation() {
        Submission submission = practice();
        submission.saveDraft("text", 1);
        submission.submit(NOW);
        assertThatThrownBy(submission::startRevision).hasMessageContaining("not evaluated");
    }

    @Test
    @DisplayName("AC-11.05.3 there is no fourth version")
    void stopsAtThree() {
        Submission submission = evaluatedDraft();
        for (int no = 2; no <= 3; no++) {
            submission.startRevision();
            submission.submit(NOW);
            submission.startEvaluation(no);
            submission.evaluated(no);
        }
        assertThatThrownBy(submission::startRevision).hasMessageContaining("at most 3");
        assertThat(submission.versions()).hasSize(3);
    }

    @Test
    @DisplayName("AC-11.06.1 a failed AI call defers the version and a retry evaluates it")
    void defersAndRetries() {
        Submission submission = practice();
        submission.saveDraft("text", 1);
        submission.submit(NOW);
        submission.startEvaluation(1);
        submission.defer(1);
        assertThat(submission.status()).isEqualTo(SubmissionStatus.EVALUATION_DEFERRED);
        assertThat(submission.awaitsEvaluation(1)).isTrue();
        submission.startEvaluation(1);
        submission.evaluated(1);
        assertThat(submission.awaitsEvaluation(1)).isFalse();
        assertThatThrownBy(() -> submission.startEvaluation(1)).isInstanceOf(SubmissionRuleException.class);
    }

    @Test
    @DisplayName("AC-11.09.1 a tutor review is kept when an AI result arrives later")
    void keepsTutorReview() {
        Submission submission = evaluatedDraft();
        submission.tutorReviewed(1);
        submission.evaluated(1);
        assertThat(submission.status()).isEqualTo(SubmissionStatus.TUTOR_REVIEWED);
        assertThatThrownBy(() -> submission.version(4)).extracting("code").isEqualTo("not-found");
        Submission draft = practice();
        assertThatThrownBy(() -> draft.tutorReviewed(1)).isInstanceOf(SubmissionRuleException.class);
    }

    @Test
    @DisplayName("INV-SUB-05 text over 5000 characters is rejected and words are counted by tokens")
    void limitsText() {
        assertThatThrownBy(() -> ResponseText.normalize("a".repeat(5001)))
                .extracting("code")
                .isEqualTo("validation-failed");
        assertThat(ResponseText.wordCount("It’s well-known: 3 cats, 2 dogs!")).isEqualTo(6);
    }

    @Test
    @DisplayName("INV-SUB-07 a draft cannot be evaluated and a submitted version cannot be edited")
    void guardsTransitions() {
        Submission submission = practice();
        assertThatThrownBy(() -> submission.startEvaluation(1)).isInstanceOf(SubmissionRuleException.class);
        submission.saveDraft("text", 1);
        submission.submit(NOW);
        assertThatThrownBy(() -> submission.saveDraft("again", 1)).isInstanceOf(SubmissionRuleException.class);
        assertThatThrownBy(() -> submission.submit(NOW)).isInstanceOf(SubmissionRuleException.class);
        assertThat(TaskType.supports("WRITE_EMAIL")).isTrue();
        assertThat(TaskType.supports("LISTEN_AND_REPEAT")).isFalse();
        assertThat(TaskType.WRITE_EMAIL.rubricId()).isEqualTo("ETS_EMAIL_V1");
    }
}
