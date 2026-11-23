package com.toefly.tutoragent.domain.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewValidatorTest {

    static WeekFacts facts(int tasksDone) {
        return new WeekFacts(
                3,
                15,
                tasksDone,
                80.0,
                null,
                List.of(new WeekFacts.SectionLine("READING", 4, 4, 60, 60)),
                List.of(new WeekFacts.ScoreLine("READ_ACADEMIC_PASSAGE", 2, 75.0, null)),
                Map.of("OVERALL", 4.0),
                Map.of("OVERALL", 3.5),
                List.of(new WeekFacts.MistakeLine(
                        "GRAMMAR.ARTICLES",
                        "GRAMMAR",
                        "Articles",
                        4,
                        List.of(new WeekFacts.Example("I study in library every day", "in the library")),
                        List.of("ROUNDUP5.U10"))),
                List.of(),
                List.of(new WeekFacts.FocusCandidate("GRAMMAR.ARTICLES", "Articles", 3.2)));
    }

    static WeeklyReview.Text valid() {
        return new WeeklyReview.Text(
                "Week 3: 80% done",
                "You finished 12 of 15 tasks and your Reading accuracy was 75%. Your estimate rose by 0.5.",
                List.of(new WeeklyReview.TopMistake(
                        "GRAMMAR.ARTICLES",
                        4,
                        "Missing the before places",
                        new WeekFacts.Example("in library", "in the library"),
                        "Study Round-Up Unit 10.",
                        List.of("ROUNDUP5.U10"))),
                List.of("All Reading tasks done"),
                List.of(new WeeklyReview.Focus("GRAMMAR.ARTICLES", "Four mistakes this week")),
                "Keep the same pace.");
    }

    @Test
    @DisplayName("a review that uses only input numbers, mistakes and examples is valid (docs/06 §5.3)")
    void validReview() {
        assertThat(ReviewValidator.violations(valid(), facts(12))).isEmpty();
    }

    @Test
    @DisplayName("invented numbers, counts, examples, focus and emoji are violations")
    void inventedContent() {
        WeeklyReview.Text bad = new WeeklyReview.Text(
                "Great week 😀",
                "You finished 14 of 15 tasks.",
                List.of(
                        new WeeklyReview.TopMistake(
                                "GRAMMAR.ARTICLES",
                                5,
                                "p",
                                new WeekFacts.Example("a made up quote", "x"),
                                "a",
                                List.of()),
                        new WeeklyReview.TopMistake("GRAMMAR.MODALS", 1, "p", null, "a", List.of())),
                List.of(),
                List.of(new WeeklyReview.Focus("VOCABULARY.REGISTER", "why")),
                "Go");
        assertThat(ReviewValidator.violations(bad, facts(12)))
                .anyMatch(v -> v.contains("14"))
                .anyMatch(v -> v.contains("count of GRAMMAR.ARTICLES must be 4"))
                .anyMatch(v -> v.contains("not quoted"))
                .anyMatch(v -> v.contains("GRAMMAR.MODALS is not in MISTAKES"))
                .anyMatch(v -> v.contains("VOCABULARY.REGISTER"))
                .anyMatch(v -> v.contains("strengths"))
                .anyMatch(v -> v.contains("emoji"));
    }

    @Test
    @DisplayName("the template uses the same facts: short when fewer than 3 tasks are done (AC-18.01.5)")
    void template() {
        WeeklyReview.Text full = WeeklyReviewTemplate.of(facts(12), ExplanationLanguage.EN);
        assertThat(ReviewValidator.violations(full, facts(12))).isEmpty();
        assertThat(full.topMistakes()).hasSize(1);
        assertThat(full.strengths()).isNotEmpty();
        WeeklyReview.Text low = WeeklyReviewTemplate.of(facts(2), ExplanationLanguage.RU);
        assertThat(facts(2).lowActivity()).isTrue();
        assertThat(low.headline()).isEqualTo("Not enough activity this week");
        assertThat(low.topMistakes()).isEmpty();
        assertThat(low.summary()).contains("2 из 15");
        assertThat(facts(12).bandDelta()).isEqualTo(0.5);
        assertThat(Emoji.strip("Good 👍")).isEqualTo("Good ");
    }
}
