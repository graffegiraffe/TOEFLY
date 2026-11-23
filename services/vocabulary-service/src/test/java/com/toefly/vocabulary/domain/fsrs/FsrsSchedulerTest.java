package com.toefly.vocabulary.domain.fsrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FsrsSchedulerTest {

    static final Instant T0 = Instant.parse("2026-11-02T09:00:00Z");

    final FsrsScheduler scheduler = new FsrsScheduler(FsrsParameters.v1().withoutFuzz());

    @Test
    @DisplayName("AC-15.03.3: the numeric example of docs/03 §11.6 (Good, then Good / Hard / Easy on day 3)")
    void numericExample() {
        assertThat(scheduler.initialStability(Rating.GOOD)).isEqualTo(3.173);
        assertThat(scheduler.initialDifficulty(Rating.GOOD)).isCloseTo(5.28, within(0.01));
        assertThat(scheduler.nextInterval(3.173)).isEqualTo(3);

        CardMemory review = new CardMemory(CardState.REVIEW, 0, 3.173, 5.2825, T0, T0, 3, 0);
        Instant day3 = T0.plus(Duration.ofDays(3));
        assertThat(scheduler.retrievability(review, day3, ZoneOffset.UTC)).isCloseTo(0.905, within(0.001));

        Map<Rating, ScheduledReview> options = scheduler.preview(review, day3, ZoneOffset.UTC, 1);
        assertThat(options.get(Rating.GOOD).after().stability()).isCloseTo(10.74, within(0.01));
        assertThat(options.get(Rating.GOOD).interval()).isEqualTo(Duration.ofDays(11));
        assertThat(options.get(Rating.HARD).after().stability()).isCloseTo(4.92, within(0.01));
        assertThat(options.get(Rating.HARD).interval()).isEqualTo(Duration.ofDays(5));
        assertThat(options.get(Rating.EASY).after().stability()).isCloseTo(25.8, within(0.05));
        assertThat(options.get(Rating.EASY).interval()).isEqualTo(Duration.ofDays(26));
    }

    @Test
    @DisplayName("AC-15.03.3: a lapse moves a REVIEW card to RELEARNING for 10 minutes and counts the lapse")
    void lapse() {
        CardMemory review = new CardMemory(CardState.REVIEW, 0, 10.74, 5.27, T0, T0, 4, 0);
        ScheduledReview again = scheduler.review(review, Rating.AGAIN, T0.plus(Duration.ofDays(18)), ZoneOffset.UTC, 1);

        assertThat(again.after().state()).isEqualTo(CardState.RELEARNING);
        assertThat(again.interval()).isEqualTo(Duration.ofMinutes(10));
        assertThat(again.after().lapses()).isEqualTo(1);
        assertThat(again.after().stability()).isLessThan(10.74).isGreaterThan(1);
        assertThat(again.elapsedDays()).isEqualTo(18);
    }

    @Test
    @DisplayName("AC-15.03.5: Again brings the card back in the same session within 1-10 minutes")
    void againWithinSession() {
        CardMemory card = CardMemory.fresh(T0);
        for (int i = 0; i < 3; i++) {
            ScheduledReview again = scheduler.review(card, Rating.AGAIN, T0.plusSeconds(60L * i), ZoneOffset.UTC, 1);
            assertThat(again.interval()).isBetween(Duration.ofMinutes(1), Duration.ofMinutes(10));
            assertThat(again.after().state()).isEqualTo(CardState.LEARNING);
            card = again.after();
        }
        assertThat(card.reps()).isEqualTo(3);
        assertThat(card.lapses()).isZero();
    }

    @Test
    @DisplayName("AC-15.03.2: the four buttons show increasing intervals for a REVIEW card")
    void buttonsAreOrdered() {
        CardMemory review = new CardMemory(CardState.REVIEW, 0, 0.8, 8.5, T0, T0, 5, 2);
        Map<Rating, ScheduledReview> options =
                scheduler.preview(review, T0.plus(Duration.ofDays(1)), ZoneOffset.UTC, 1);

        assertThat(options.get(Rating.AGAIN).interval()).isEqualTo(Duration.ofMinutes(10));
        assertThat(options.get(Rating.HARD).interval())
                .isLessThan(options.get(Rating.GOOD).interval());
        assertThat(options.get(Rating.GOOD).interval())
                .isLessThan(options.get(Rating.EASY).interval());
    }

    @Test
    @DisplayName("Phase 8 DoD: a REVIEW card is due at the start of the learner's local day")
    void dueAtLocalMidnight() {
        ZoneId minsk = ZoneId.of("Europe/Minsk");
        Instant evening = Instant.parse("2026-11-02T20:30:00Z"); // 23:30 in Minsk
        ScheduledReview easy = scheduler.review(CardMemory.fresh(evening), Rating.EASY, evening, minsk, 1);

        assertThat(easy.interval()).isEqualTo(Duration.ofDays(16));
        assertThat(easy.after().due()).isEqualTo(Instant.parse("2026-11-17T21:00:00Z")); // 18 Nov 00:00 Minsk
    }

    @Test
    @DisplayName("Phase 8 DoD: elapsed days count local midnights, so a review after midnight is a new day")
    void elapsedDaysAreLocal() {
        ZoneId minsk = ZoneId.of("Europe/Minsk");
        CardMemory learned = new CardMemory(
                CardState.REVIEW,
                0,
                3.173,
                5.28,
                Instant.parse("2026-11-03T21:00:00Z"),
                Instant.parse("2026-11-02T20:00:00Z"),
                2,
                0);
        ScheduledReview good = scheduler.review(learned, Rating.GOOD, Instant.parse("2026-11-02T21:30:00Z"), minsk, 1);

        assertThat(good.elapsedDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("docs/03 §11.2: fuzz is deterministic, within ±5% (at least one day) and only from 3 days")
    void fuzz() {
        FsrsScheduler fuzzy = new FsrsScheduler(FsrsParameters.v1());
        assertThat(fuzzy.fuzz(2, 42, 1)).isEqualTo(2);
        assertThat(fuzzy.fuzz(100, 42, 5)).isEqualTo(fuzzy.fuzz(100, 42, 5));
        for (long seed = 0; seed < 200; seed++) {
            assertThat(fuzzy.fuzz(3, seed, 1)).isBetween(2, 4);
            assertThat(fuzzy.fuzz(100, seed, 1)).isBetween(95, 105);
            assertThat(fuzzy.fuzz(365, seed, 1)).isBetween(347, 365);
        }
    }

    @Test
    @DisplayName("docs/03 §11.3: retrievability is 0.9 after S days and 0 for a card never reviewed")
    void retrievability() {
        assertThat(FsrsScheduler.retrievability(7, 7)).isCloseTo(0.9, within(1e-9));
        assertThat(FsrsScheduler.retrievability(1, 0)).isZero();
        assertThat(scheduler.retrievability(CardMemory.fresh(T0), T0, ZoneOffset.UTC))
                .isZero();
    }

    @Test
    @DisplayName("fsrs-params-v1 rejects a wrong number of weights and keeps the docs values")
    void parameters() {
        FsrsParameters v1 = FsrsParameters.v1();
        assertThat(v1.version()).isEqualTo("fsrs-params-v1");
        assertThat(v1.w(2)).isEqualTo(3.173);
        assertThat(v1.maximumIntervalDays()).isEqualTo(365);
        assertThat(FsrsParameters.FACTOR).isCloseTo(19.0 / 81, within(1e-12));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new FsrsParameters(
                        "x", new double[3], 0.9, 365, v1.learningSteps(), v1.relearningSteps(), false));
        assertThat(Rating.ofGrade(4)).isEqualTo(Rating.EASY);
    }
}
