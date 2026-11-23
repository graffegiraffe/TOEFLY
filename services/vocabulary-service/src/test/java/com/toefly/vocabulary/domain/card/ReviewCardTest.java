package com.toefly.vocabulary.domain.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.vocabulary.domain.fsrs.CardMemory;
import com.toefly.vocabulary.domain.fsrs.CardState;
import com.toefly.vocabulary.domain.fsrs.FsrsParameters;
import com.toefly.vocabulary.domain.fsrs.FsrsScheduler;
import com.toefly.vocabulary.domain.fsrs.Rating;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewCardTest {

    static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    final FsrsScheduler scheduler = new FsrsScheduler(FsrsParameters.v1());

    @Test
    @DisplayName("AC-15.01.2: a new entry gets a card in the state New")
    void created() {
        ReviewCard card =
                ReviewCard.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "fsrs-params-v1", NOW);
        assertThat(card.state()).isEqualTo(CardState.NEW);
        assertThat(card.status()).isEqualTo(VocabularyStatus.NEW);
        assertThat(card.memory().due()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("AC-15.03.3: a grade recomputes the memory and writes the log with values before and after")
    void review() {
        ReviewCard card =
                ReviewCard.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "fsrs-params-v1", NOW);
        UUID session = UUID.randomUUID();
        ReviewCard.Reviewed reviewed =
                card.review(scheduler, Rating.GOOD, NOW, ZoneOffset.UTC, UUID.randomUUID(), session, 1, 4200);

        assertThat(reviewed.card().state()).isEqualTo(CardState.LEARNING);
        assertThat(reviewed.card().memory().reps()).isEqualTo(1);
        assertThat(reviewed.log().stateBefore()).isEqualTo(CardState.NEW);
        assertThat(reviewed.log().stabilityBefore()).isZero();
        assertThat(reviewed.log().stabilityAfter()).isEqualTo(3.173);
        assertThat(reviewed.log().sessionId()).isEqualTo(session);
        assertThat(reviewed.log().durationMs()).isEqualTo(4200);
        assertThat(reviewed.log().scheduledDays()).isCloseTo(10.0 / 1440, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    @DisplayName("AC-15.06.2: a suspended card keeps its state and cannot be graded until resumed")
    void suspend() {
        ReviewCard card = ReviewCard.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "v", NOW)
                .suspend();
        assertThat(card.suspended()).isTrue();
        assertThatThrownBy(
                        () -> card.review(scheduler, Rating.GOOD, NOW, ZoneOffset.UTC, UUID.randomUUID(), null, 0, 0))
                .isInstanceOf(CardSuspendedException.class);
        assertThat(card.resume().suspended()).isFalse();
    }

    @Test
    @DisplayName("AC-15.06.3: Mastered means FSRS stability of 90 days or more")
    void mastered() {
        assertThat(withMemory(CardState.REVIEW, 90).status()).isEqualTo(VocabularyStatus.MASTERED);
        assertThat(withMemory(CardState.REVIEW, 89.9).status()).isEqualTo(VocabularyStatus.REVIEW);
        assertThat(withMemory(CardState.RELEARNING, 120).status()).isEqualTo(VocabularyStatus.LEARNING);
    }

    @Test
    @DisplayName("AC-15.03.2: the preview gives the interval under each button")
    void preview() {
        ReviewCard card = withMemory(CardState.REVIEW, 10);
        assertThat(card.preview(scheduler, NOW.plusSeconds(86_400 * 10), ZoneOffset.UTC))
                .hasSize(4);
    }

    private static ReviewCard withMemory(CardState state, double stability) {
        return new ReviewCard(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new CardMemory(state, 0, stability, 5, NOW, NOW, 3, 0),
                false,
                "v",
                NOW,
                0);
    }
}
