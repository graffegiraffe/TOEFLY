package com.toefly.practice.domain.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.practice.domain.attempt.Answer;
import com.toefly.practice.domain.attempt.ItemOutcome;
import com.toefly.practice.domain.attempt.PinnedItem;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttemptScorerTest {

    static final PinnedItem ITEM = new PinnedItem(UUID.randomUUID(), 1, 0.3, 1);

    @Test
    @DisplayName("AC-07.01.4: a gap is right when the letters match the key or an accepted variant, case aside")
    void completeTheWords() {
        ItemKey key = new ItemKey.Text(1, List.of("ter", "ters"), true);

        assertThat(score(key, new Answer.Text("TER")).correct()).isTrue();
        assertThat(score(key, new Answer.Text(" ters ")).correct()).isTrue();
        assertThat(score(key, new Answer.Text("te")).correct()).isFalse();
        assertThat(score(key, new Answer.Text("te")).score()).isZero();
    }

    @Test
    @DisplayName("AC-07.02.2: multiple choice has one right option")
    void singleChoice() {
        ItemKey key = new ItemKey.SingleChoice(1, "B");

        assertThat(score(key, choice("B")).correct()).isTrue();
        assertThat(score(key, choice("A")).correct()).isFalse();
        assertThat(score(key, new Answer.Choice(List.of("A", "B"))).correct()).isFalse();
        assertThat(score(key, new Answer.Text("B")).correct()).isFalse();
    }

    @Test
    @DisplayName("AC-09.01.4: Build a Sentence needs a key sequence, all slots filled and no distractor")
    void buildASentence() {
        ItemKey key = new ItemKey.Sequence(1, List.of(List.of("c1", "c2", "c3"), List.of("c2", "c1", "c3")), "c9", 3);

        assertThat(score(key, sequence("c1", "c2", "c3")).correct()).isTrue();
        assertThat(score(key, sequence("c2", "c1", "c3")).correct()).isTrue();
        assertThat(score(key, sequence("c1", "c3", "c2")).correct()).isFalse();
        assertThat(score(key, sequence("c1", "c2", null)).correct()).isFalse();
        assertThat(score(key, sequence("c1", "c9", "c3")).correct()).isFalse();
        assertThat(score(key, sequence("c1", "c2")).correct()).isFalse();
        assertThat(score(key, choice("c1")).correct()).isFalse();
    }

    @Test
    @DisplayName("docs/03 §6.5: a prose summary gives 2 points for three right choices, 1 for two, otherwise 0")
    void proseSummary() {
        ItemKey key = new ItemKey.ProseSummary(1, Set.of("A", "C", "E"));

        assertThat(score(key, new Answer.Choice(List.of("A", "C", "E"))).score())
                .isEqualTo(2);
        assertThat(score(key, new Answer.Choice(List.of("A", "C", "E"))).correct())
                .isTrue();
        assertThat(score(key, new Answer.Choice(List.of("A", "C", "B"))).score())
                .isEqualTo(1);
        assertThat(score(key, new Answer.Choice(List.of("A", "C", "B"))).correct())
                .isFalse();
        assertThat(score(key, new Answer.Choice(List.of("A", "B", "D"))).score())
                .isZero();
        assertThat(score(key, new Answer.Choice(List.of("A", "B", "C", "E"))).score())
                .isZero();
        assertThat(score(key, new Answer.Text("A")).score()).isZero();
    }

    @Test
    @DisplayName("docs/03 §6.5: a legacy multi-select is right only for the exact set")
    void multiSelect() {
        ItemKey key = new ItemKey.MultiSelect(1, Set.of("A", "D"));

        assertThat(score(key, new Answer.Choice(List.of("D", "A"))).correct()).isTrue();
        assertThat(score(key, new Answer.Choice(List.of("A"))).correct()).isFalse();
        assertThat(score(key, new Answer.Text("A")).correct()).isFalse();
    }

    @Test
    @DisplayName("AC-14.04.2: gap-fill ignores case, extra spaces and typographic apostrophes")
    void gapFill() {
        ItemKey key = new ItemKey.Text(1, List.of("isn't working"), false);

        assertThat(score(key, new Answer.Text("  Isn’t   WORKING ")).correct()).isTrue();
        assertThat(score(key, new Answer.Text("is not working")).correct()).isFalse();
        assertThat(score(key, choice("A")).correct()).isFalse();
    }

    @Test
    @DisplayName("AC-14.04.3: a transformation that matches no accepted answer waits for the AI check")
    void transformation() {
        ItemKey key = new ItemKey.Transformation(1, List.of("He has lived here since 2010."));

        ItemOutcome exact = score(key, new Answer.Text("he has lived here since 2010"));
        ItemOutcome other = score(key, new Answer.Text("He's been living here since 2010."));

        assertThat(exact.correct()).isTrue();
        assertThat(exact.pending()).isFalse();
        assertThat(other.correct()).isFalse();
        assertThat(other.pending()).isTrue();
        assertThat(score(key, choice("A")).pending()).isFalse();
    }

    @Test
    @DisplayName("AC-14.04.1: error correction needs the faulty fragment and an accepted correction")
    void errorCorrection() {
        ItemKey key = new ItemKey.ErrorCorrection(1, "B", List.of("went"));

        assertThat(score(key, mapping("B", " Went")).correct()).isTrue();
        assertThat(score(key, mapping("C", "went")).correct()).isFalse();
        assertThat(score(key, mapping("B", "goed")).correct()).isFalse();
        assertThat(score(
                                key,
                                new Answer.Mapping(List.of(
                                        new Answer.Mapping.Pair("B", "went"), new Answer.Mapping.Pair("C", "x"))))
                        .correct())
                .isFalse();
        assertThat(score(key, new Answer.Text("went")).correct()).isFalse();
    }

    @Test
    @DisplayName("an unanswered item and an item without a key score 0 and are reported as such")
    void unansweredAndKeyless() {
        PinnedItem second = new PinnedItem(UUID.randomUUID(), 2, null, 1);
        List<ItemOutcome> outcomes = AttemptScorer.score(
                List.of(ITEM, second),
                Map.of(1, new ItemKey.SingleChoice(1, "A")),
                Map.of(second.itemId(), choice("A")));

        assertThat(outcomes.get(0).answered()).isFalse();
        assertThat(outcomes.get(0).b()).isEqualTo(0.3);
        assertThat(outcomes.get(1).answered()).isTrue();
        assertThat(outcomes.get(1).correct()).isFalse();
    }

    @Test
    @DisplayName("every key kind shows a correct answer in the review")
    void correctAnswers() {
        assertThat(new ItemKey.SingleChoice(1, "A").correctAnswer()).isEqualTo(choice("A"));
        assertThat(new ItemKey.Text(1, List.of("ter"), true).correctAnswer()).isEqualTo(new Answer.Text("ter"));
        assertThat(new ItemKey.Transformation(1, List.of("x")).correctAnswer()).isEqualTo(new Answer.Text("x"));
        assertThat(new ItemKey.Sequence(1, List.of(List.of("c1")), null, 1).correctAnswer())
                .isEqualTo(sequence("c1"));
        assertThat(new ItemKey.ErrorCorrection(1, "B", List.of("went")).correctAnswer())
                .isEqualTo(mapping("B", "went"));
        assertThat(new ItemKey.ProseSummary(1, Set.of("C", "A", "E")).correctAnswer())
                .isEqualTo(new Answer.Choice(List.of("A", "C", "E")));
        assertThat(new ItemKey.MultiSelect(1, Set.of("D", "A")).correctAnswer())
                .isEqualTo(new Answer.Choice(List.of("A", "D")));
        assertThat(new ItemSetKeys(Map.of(), new HashMap<>()).keys()).isEmpty();
        assertThat(new ItemExplanation(null, Map.of("A", "x")).rationales()).containsKey("A");
    }

    private static ItemOutcome score(ItemKey key, Answer answer) {
        return AttemptScorer.score(List.of(ITEM), Map.of(1, key), Map.of(ITEM.itemId(), answer))
                .getFirst();
    }

    private static Answer choice(String key) {
        return new Answer.Choice(List.of(key));
    }

    private static Answer sequence(String... ids) {
        return new Answer.Sequence(Arrays.asList(ids));
    }

    private static Answer mapping(String key, String value) {
        return new Answer.Mapping(List.of(new Answer.Mapping.Pair(key, value)));
    }
}
