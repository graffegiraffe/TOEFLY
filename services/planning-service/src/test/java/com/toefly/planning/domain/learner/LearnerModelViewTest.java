package com.toefly.planning.domain.learner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the learner model projection (docs/06-ai-agent.md §5.2, §6.2)")
class LearnerModelViewTest {

    @Test
    @DisplayName("subcategory codes get their category, the highest priority comes first")
    void normalises() {
        LearnerModelView model = new LearnerModelView(
                UUID.randomUUID(),
                3,
                Instant.EPOCH,
                List.of(
                        new MistakeStat("task_response", "ELABORATION", 0.4, "watch", 1, List.of()),
                        new MistakeStat("GRAMMAR", "GRAMMAR.ARTICLES", 2.1, "ACTIVE", 4, List.of("ROUNDUP5.U10"))),
                Map.of("OVERALL", 4.0));

        assertThat(model.mistakes())
                .extracting(MistakeStat::subcategory)
                .containsExactly("GRAMMAR.ARTICLES", "TASK_RESPONSE.ELABORATION");
        assertThat(model.mistakes().get(1).status()).isEqualTo("WATCH");
    }

    @Test
    @DisplayName("AC-14.05.2: a grammar topic met 3 times in 7 days needs practice; resolved topics are not active")
    void grammarPractice() {
        LearnerModelView model = new LearnerModelView(
                UUID.randomUUID(),
                1,
                Instant.EPOCH,
                List.of(
                        new MistakeStat("GRAMMAR", "ARTICLES", 2.0, "ACTIVE", 3, List.of("ROUNDUP5.U10")),
                        new MistakeStat("GRAMMAR", "VERB_TENSES", 1.5, "ACTIVE", 2, List.of("ROUNDUP5.U01")),
                        new MistakeStat("VOCABULARY", "WORD_CHOICE", 1.0, "ACTIVE", 5, List.of()),
                        new MistakeStat("GRAMMAR", "PASSIVE", 0.5, "RESOLVED", 0, List.of())),
                Map.of());

        assertThat(model.grammarPractice()).extracting(MistakeStat::subcategory).containsExactly("GRAMMAR.ARTICLES");
        assertThat(model.active()).hasSize(3);
        assertThat(LearnerModelView.empty(UUID.randomUUID()).mistakes()).isEmpty();
    }
}
