package com.toefly.practice.domain.attempt;

import static com.toefly.practice.domain.attempt.Attempts.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttemptTest {

    @Test
    @DisplayName("AC-06.01.1: a new attempt is IN_PROGRESS and its stream starts with AttemptStarted")
    void startsInProgress() {
        Attempt attempt = Attempts.practice(5);

        assertThat(attempt.status()).isEqualTo(AttemptStatus.IN_PROGRESS);
        assertThat(attempt.pendingEvents()).singleElement().isInstanceOf(AttemptEvent.AttemptStarted.class);
        assertThat(attempt.version()).isEqualTo(1);
        assertThat(attempt.persistedVersion()).isZero();
    }

    @Test
    @DisplayName("AC-06.01.4: the learner changes an answer and the last one wins; the same answer adds no event")
    void lastAnswerWins() {
        Attempt attempt = Attempts.practice(5);
        UUID item = attempt.startedEvent().items().getFirst().itemId();

        attempt.recordAnswer(item, choice("A"), T0.plusSeconds(10), null, null);
        attempt.recordAnswer(item, choice("C"), T0.plusSeconds(20), null, null);
        attempt.recordAnswer(item, choice("C"), T0.plusSeconds(30), null, null);

        assertThat(attempt.answers()).containsEntry(item, choice("C"));
        assertThat(attempt.pendingEvents()).hasSize(3);
    }

    @Test
    @DisplayName("an empty answer clears the item; clearing an unanswered item adds no event")
    void blankAnswerClears() {
        Attempt attempt = Attempts.practice(2);
        UUID item = attempt.startedEvent().items().getFirst().itemId();
        attempt.recordAnswer(item, new Answer.Text("ab"), T0.plusSeconds(1), null, null);

        attempt.recordAnswer(item, new Answer.Text("  "), T0.plusSeconds(2), null, null);
        attempt.clearAnswer(item, T0.plusSeconds(3));

        assertThat(attempt.answers()).isEmpty();
        assertThat(attempt.pendingEvents())
                .extracting(Object::getClass)
                .containsExactly(
                        AttemptEvent.AttemptStarted.class,
                        AttemptEvent.AnswerRecorded.class,
                        AttemptEvent.AnswerCleared.class);
    }

    @Test
    @DisplayName("an answer to an item outside the attempt is rejected")
    void unknownItem() {
        Attempt attempt = Attempts.practice(2);

        assertThatThrownBy(() -> attempt.recordAnswer(UUID.randomUUID(), choice("A"), T0, null, null))
                .isInstanceOf(UnknownItemException.class);
    }

    @Test
    @DisplayName("INV-ATT-02: answers are accepted only while the attempt is IN_PROGRESS")
    void noAnswersAfterSubmission() {
        Attempt attempt = Attempts.practice(2);
        UUID item = attempt.startedEvent().items().getFirst().itemId();
        attempt.submit(T0.plusSeconds(60), SubmitReason.USER, 40);

        assertThatThrownBy(() -> attempt.recordAnswer(item, choice("A"), T0.plusSeconds(61), null, null))
                .isInstanceOf(AttemptAlreadySubmittedException.class);
        assertThatThrownBy(() -> attempt.submit(T0.plusSeconds(62), SubmitReason.USER, 40))
                .isInstanceOf(AttemptAlreadySubmittedException.class);
    }

    @Test
    @DisplayName("INV-ATT-02: an EXAM answer more than 2 s after the deadline is rejected")
    void examDeadline() {
        Attempt attempt = Attempt.start(Attempts.started(AttemptMode.EXAM, 2, T0.plusSeconds(600)));
        UUID item = attempt.startedEvent().items().getFirst().itemId();

        attempt.recordAnswer(item, choice("A"), T0.plusSeconds(601), null, null);

        assertThatThrownBy(() -> attempt.recordAnswer(item, choice("B"), T0.plusSeconds(603), null, null))
                .isInstanceOf(AttemptTimeExpiredException.class);
    }

    @Test
    @DisplayName("INV-ATT-07: an EXAM attempt cannot be paused")
    void noPauseInExam() {
        Attempt attempt = Attempt.start(Attempts.started(AttemptMode.EXAM, 2, T0.plusSeconds(600)));

        assertThatThrownBy(() -> attempt.pause(T0.plusSeconds(5))).isInstanceOf(AttemptStateConflictException.class);
        assertThatThrownBy(() -> attempt.resume(T0.plusSeconds(5))).isInstanceOf(AttemptStateConflictException.class);
    }

    @Test
    @DisplayName("AC-06.01.3: a Practice pause is counted in pausedSeconds and does not repeat")
    void pauseAndResume() {
        Attempt attempt = Attempts.practice(2);

        attempt.pause(T0.plusSeconds(10));
        attempt.pause(T0.plusSeconds(11));
        attempt.resume(T0.plusSeconds(70));
        attempt.resume(T0.plusSeconds(71));

        assertThat(attempt.snapshot().pausedSeconds()).isEqualTo(60);
        assertThat(attempt.paused()).isFalse();
        assertThat(attempt.pendingEvents()).hasSize(3);
    }

    @Test
    @DisplayName("AC-06.01.5: unanswered items are scored as wrong; INV-ATT-04: scoring twice adds nothing")
    void scoring() {
        Attempt attempt = Attempts.practice(3);
        List<PinnedItem> items = attempt.startedEvent().items();
        attempt.recordAnswer(items.get(0).itemId(), choice("A"), T0.plusSeconds(5), null, null);
        attempt.submit(T0.plusSeconds(60), SubmitReason.USER, 55);

        List<ItemOutcome> outcomes = List.of(new ItemOutcome(
                items.get(0).itemId(), 1, 1, 1, true, true, false, items.get(0).b()));
        attempt.score(outcomes, T0.plusSeconds(60));
        int events = attempt.pendingEvents().size();
        attempt.score(outcomes, T0.plusSeconds(61));

        AttemptSnapshot state = attempt.snapshot();
        assertThat(state.status()).isEqualTo(AttemptStatus.SCORED);
        assertThat(state.rawScore()).isEqualTo(1.0);
        assertThat(state.correctCount()).isEqualTo(1);
        assertThat(state.unansweredCount()).isEqualTo(2);
        assertThat(state.scores()).hasSize(3);
        assertThat(attempt.pendingEvents()).hasSize(events);
    }

    @Test
    @DisplayName("only a submitted attempt can be scored")
    void scoringRequiresSubmission() {
        Attempt attempt = Attempts.practice(1);

        assertThatThrownBy(() -> attempt.score(List.of(), T0)).isInstanceOf(AttemptStateConflictException.class);
    }

    @Test
    @DisplayName(
            "AC-06.03.3: an abandoned attempt ends; abandoning twice does nothing, a scored one cannot be abandoned")
    void abandon() {
        Attempt attempt = Attempts.practice(1);
        attempt.pause(T0.plusSeconds(1));

        attempt.abandon(T0.plusSeconds(31), AbandonReason.USER_EXIT);
        attempt.abandon(T0.plusSeconds(32), AbandonReason.USER_EXIT);

        assertThat(attempt.status()).isEqualTo(AttemptStatus.ABANDONED);
        assertThat(attempt.snapshot().pausedSeconds()).isEqualTo(30);
        assertThatThrownBy(() -> attempt.submit(T0.plusSeconds(40), SubmitReason.USER, 0))
                .isInstanceOf(AttemptAlreadySubmittedException.class);

        Attempt scored = Attempts.practice(1);
        scored.submit(T0.plusSeconds(5), SubmitReason.USER, 5);
        assertThatThrownBy(() -> scored.abandon(T0.plusSeconds(6), AbandonReason.USER_EXIT))
                .isInstanceOf(AttemptAlreadySubmittedException.class);
    }

    @Test
    @DisplayName("a stream must start with AttemptStarted")
    void replayNeedsStart() {
        assertThatThrownBy(() -> Attempt.replay(List.of(new AttemptEvent.AttemptPaused(T0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("markPersisted moves the expected version to the written one")
    void persistence() {
        Attempt attempt = Attempts.practice(1);
        attempt.markPersisted();
        assertThat(attempt.persistedVersion()).isEqualTo(1);
        assertThat(attempt.pendingEvents()).isEmpty();

        Attempt loaded = Attempt.replay(List.of(attempt.startedEvent()));
        assertThat(loaded.id()).isEqualTo(attempt.id());
        assertThat(loaded.userId()).isEqualTo(attempt.userId());
        assertThat(loaded.submittedAt()).isNull();
    }

    private static Answer choice(String key) {
        return new Answer.Choice(List.of(key));
    }
}
