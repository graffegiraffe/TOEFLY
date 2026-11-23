package com.toefly.vocabulary.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.vocabulary.domain.card.ReviewCard;
import com.toefly.vocabulary.domain.fsrs.CardState;
import com.toefly.vocabulary.domain.fsrs.Rating;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewSessionTest {

    static final Instant NOW = Instant.parse("2026-11-23T07:00:00Z");

    @Test
    @DisplayName("AC-15.03.3: a session counts grades, lapses and new cards until it is completed")
    void lifecycle() {
        ReviewCard a = DailyQueueTest.card(CardState.NEW, NOW, 0);
        ReviewCard b = DailyQueueTest.card(CardState.REVIEW, NOW, 4);
        DailyQueue queue = new DailyQueue(LocalDate.of(2026, 11, 23), List.of(a, b), 1, 1, 0);
        ReviewSession session =
                ReviewSession.start(UUID.randomUUID(), UUID.randomUUID(), null, queue, "Europe/Minsk", NOW);

        session = session.graded(a.id(), Rating.AGAIN, CardState.NEW, 3000);
        assertThat(session.allPlannedReviewed()).isFalse();
        session = session.graded(a.id(), Rating.GOOD, CardState.LEARNING, 2000);
        session = session.graded(b.id(), Rating.GOOD, CardState.REVIEW, 900_000);

        assertThat(session.allPlannedReviewed()).isTrue();
        assertThat(session.grades()).isEqualTo(3);
        assertThat(session.againCount()).isEqualTo(1);
        assertThat(session.newIntroduced()).isEqualTo(1);
        assertThat(session.activeSeconds()).isEqualTo(605);
        assertThat(session.planned(b.id())).isTrue();

        ReviewSession completed = session.complete(NOW.plusSeconds(700));
        assertThat(completed.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThatThrownBy(() -> completed.complete(NOW)).isInstanceOf(SessionStateException.class);
        assertThatThrownBy(() -> completed.graded(a.id(), Rating.GOOD, CardState.REVIEW, 0))
                .isInstanceOf(SessionStateException.class);
    }

    @Test
    @DisplayName("AC-15.03.2: formats alternate word → definition, definition → word, cloze")
    void formats() {
        assertThat(CardFormat.choose(0, 0, true, true)).isEqualTo(CardFormat.WORD_TO_DEFINITION);
        assertThat(CardFormat.choose(1, 0, true, true)).isEqualTo(CardFormat.DEFINITION_TO_WORD);
        assertThat(CardFormat.choose(2, 0, true, true)).isEqualTo(CardFormat.CLOZE);
        assertThat(CardFormat.choose(2, 0, true, false)).isEqualTo(CardFormat.DEFINITION_TO_WORD);
        assertThat(CardFormat.choose(1, 0, false, true)).isEqualTo(CardFormat.WORD_TO_DEFINITION);
        assertThat(CardFormat.choose(2, 0, false, false)).isEqualTo(CardFormat.WORD_TO_DEFINITION);
    }
}
