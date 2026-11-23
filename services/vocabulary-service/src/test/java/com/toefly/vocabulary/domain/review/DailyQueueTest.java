package com.toefly.vocabulary.domain.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.vocabulary.domain.card.ReviewCard;
import com.toefly.vocabulary.domain.fsrs.CardMemory;
import com.toefly.vocabulary.domain.fsrs.CardState;
import com.toefly.vocabulary.domain.fsrs.FsrsParameters;
import com.toefly.vocabulary.domain.fsrs.FsrsScheduler;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DailyQueueTest {

    static final ZoneId MINSK = ZoneId.of("Europe/Minsk");
    static final Instant NOW = Instant.parse("2026-11-23T07:00:00Z"); // 10:00 in Minsk
    final FsrsScheduler scheduler = new FsrsScheduler(FsrsParameters.v1());

    @Test
    @DisplayName("AC-15.03.1: the day holds all cards due by the end of the local day plus up to 10 new cards")
    void dueAndNew() {
        List<ReviewCard> cards = new ArrayList<>();
        cards.add(card(CardState.REVIEW, Instant.parse("2026-11-23T20:59:00Z"), 5)); // 23:59 today
        cards.add(card(CardState.REVIEW, Instant.parse("2026-11-23T21:00:00Z"), 5)); // tomorrow 00:00
        cards.add(card(CardState.LEARNING, NOW.minusSeconds(60), 1));
        cards.add(card(CardState.LEARNING, NOW.plusSeconds(300), 1)); // not yet
        for (int i = 0; i < 15; i++) {
            cards.add(card(CardState.NEW, NOW, 0));
        }
        cards.add(card(CardState.REVIEW, NOW.minusSeconds(3600), 5).suspend());

        DailyQueue queue = DailyQueue.build(cards, scheduler, NOW, MINSK, DailyLimits.defaults(), 0, 0);

        assertThat(queue.localDate()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(queue.dueCount()).isEqualTo(2);
        assertThat(queue.newCount()).isEqualTo(10);
        assertThat(queue.cards()).hasSize(12);
        assertThat(queue.cards().get(0).state()).isEqualTo(CardState.LEARNING);
        assertThat(queue.dueTomorrow()).isEqualTo(4);
    }

    @Test
    @DisplayName("AC-15.03.1: never more than 100 cards a day; new cards respect what was introduced today")
    void caps() {
        List<ReviewCard> cards = new ArrayList<>();
        for (int i = 0; i < 95; i++) {
            cards.add(card(CardState.REVIEW, NOW.minusSeconds(60), 5));
        }
        for (int i = 0; i < 20; i++) {
            cards.add(card(CardState.NEW, NOW.plusSeconds(i), 0));
        }

        DailyQueue capped = DailyQueue.build(cards, scheduler, NOW, MINSK, DailyLimits.defaults(), 0, 0);
        assertThat(capped.cards()).hasSize(100);
        assertThat(capped.newCount()).isEqualTo(5);

        DailyQueue later = DailyQueue.build(cards.subList(95, 115), scheduler, NOW, MINSK, DailyLimits.of(12), 8, 8);
        assertThat(later.newCount()).isEqualTo(4);

        DailyQueue full = DailyQueue.build(cards, scheduler, NOW, MINSK, DailyLimits.defaults(), 10, 100);
        assertThat(full.cards()).isEmpty();
        assertThat(full.dueCount()).isEqualTo(95);
    }

    @Test
    @DisplayName("docs/03 §11.5: review cards come by ascending retrievability, new cards in creation order")
    void order() {
        ReviewCard weak = card(CardState.REVIEW, NOW.minus(Duration.ofDays(10)), 2);
        ReviewCard strong = card(CardState.REVIEW, NOW.minus(Duration.ofDays(1)), 50);
        ReviewCard first = card(CardState.NEW, NOW.minusSeconds(100), 0);
        ReviewCard second = card(CardState.NEW, NOW.minusSeconds(50), 0);

        DailyQueue queue = DailyQueue.build(
                List.of(second, strong, first, weak), scheduler, NOW, MINSK, DailyLimits.defaults(), 0, 0);

        assertThat(queue.cards()).containsExactly(weak, strong, first, second);
    }

    @Test
    @DisplayName("AC-15.03.4: no cards means an empty queue")
    void empty() {
        DailyQueue queue = DailyQueue.build(List.of(), scheduler, NOW, MINSK, DailyLimits.defaults(), 0, 0);
        assertThat(queue.cards()).isEmpty();
        assertThat(queue.dueTomorrow()).isZero();
    }

    @Test
    @DisplayName("INV-VOC-05: new cards per day stay within 5-30")
    void limits() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> DailyLimits.of(4));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> DailyLimits.of(31));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new DailyLimits(10, 101));
    }

    static ReviewCard card(CardState state, Instant due, double stability) {
        Instant last = state == CardState.NEW ? null : due.minus(Duration.ofDays(Math.max(1, (long) stability)));
        return new ReviewCard(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new CardMemory(state, 0, stability, state == CardState.NEW ? 0 : 5, due, last, 1, 0),
                false,
                "fsrs-params-v1",
                due,
                0);
    }
}
