package com.toefly.tutoragent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.tutoragent.application.port.in.EntitlementRequiredException;
import com.toefly.tutoragent.application.port.in.LearnerModelUseCase.ScoredAttempt;
import com.toefly.tutoragent.application.port.in.LearnerModelUseCase.ScoredItem;
import com.toefly.tutoragent.application.port.out.AssessmentPort;
import com.toefly.tutoragent.application.port.out.Cause;
import com.toefly.tutoragent.application.port.out.ClosedWeek;
import com.toefly.tutoragent.application.port.out.PracticePort;
import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import com.toefly.tutoragent.domain.learner.MistakeRecord;
import com.toefly.tutoragent.domain.learner.Severity;
import com.toefly.tutoragent.domain.review.WeeklyReview;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LearnerAndReviewScenarioTest {

    final UUID user = UUID.randomUUID();
    final Fakes.Models models = new Fakes.Models();
    final Fakes.Mistakes mistakes = new Fakes.Mistakes();
    final Fakes.Memory memory = new Fakes.Memory();
    final Fakes.Publications publications = new Fakes.Publications();
    final Fakes.LearnerEvents learnerEvents = new Fakes.LearnerEvents();
    final Fakes.Results results = new Fakes.Results();
    final Fakes.Practice practice = new Fakes.Practice();
    final Fakes.Assessment assessment = new Fakes.Assessment();
    final Fakes.Reviews reviews = new Fakes.Reviews();
    final Fakes.DeferredRows deferred = new Fakes.DeferredRows();
    final Fakes.Rights rights = new Fakes.Rights();
    final Fakes.ReviewEvents reviewEvents = new Fakes.ReviewEvents();
    final Fakes.Ai ai = new Fakes.Ai();
    LearnerModelService learner;
    WeeklyReviewService weekly;

    @BeforeEach
    void setUp() {
        learner = new LearnerModelService(
                models,
                mistakes,
                memory,
                publications,
                learnerEvents,
                results,
                practice,
                assessment,
                Fakes.taxonomy(),
                Fakes.TX,
                Fakes.clock());
        weekly = new WeeklyReviewService(
                learner,
                models,
                mistakes,
                results,
                reviews,
                deferred,
                rights,
                ai,
                reviewEvents,
                Fakes.taxonomy(),
                Fakes.TX,
                Fakes.clock());
    }

    void scoredAttempt() {
        UUID attempt = UUID.randomUUID();
        UUID wrong = UUID.randomUUID();
        UUID right = UUID.randomUUID();
        practice.reviews.put(
                attempt,
                new PracticePort.AttemptReview(
                        attempt,
                        "COMPLETE_THE_WORDS",
                        List.of(
                                new PracticePort.ItemReview(
                                        wrong, 1, false, true, "enviroment", "environment", "Spell it."),
                                new PracticePort.ItemReview(right, 2, true, true, "house", "house", null))));
        learner.attemptScored(
                new ScoredAttempt(
                        user,
                        attempt,
                        "COMPLETE_THE_WORDS",
                        Fakes.NOW.minus(Duration.ofDays(1)),
                        List.of(new ScoredItem(wrong, true, false), new ScoredItem(right, true, true))),
                Cause.none());
    }

    void evaluatedEmail() {
        UUID submission = UUID.randomUUID();
        assessment.submissions.put(
                submission,
                new AssessmentPort.Submission(
                        submission,
                        user,
                        "WRITE_EMAIL",
                        "EVALUATED",
                        List.of(new AssessmentPort.Version(
                                1,
                                "EVALUATED",
                                "I study in library every day.",
                                6,
                                3.5,
                                List.of(
                                        new AssessmentPort.Comment(
                                                "in library",
                                                "Use the article.",
                                                "in the library",
                                                "GRAMMAR",
                                                "ARTICLES",
                                                "MAJOR"),
                                        new AssessmentPort.Comment(
                                                "every day", "Fine.", null, "VOCABULARY", "WORD_CHOICE", "MINOR")),
                                Fakes.NOW)),
                        null));
        learner.submissionEvaluated(user, submission, 1, Fakes.NOW.minus(Duration.ofHours(2)), Cause.none());
    }

    @Test
    @DisplayName("closed and writing mistakes become records, opportunities and memory (docs/06 §6.2, AC-14.05.1)")
    void learnerModelFromEvents() {
        scoredAttempt();
        evaluatedEmail();
        assertThat(mistakes.records)
                .extracting(MistakeRecord::subcategory)
                .containsExactlyInAnyOrder("SPELLING.COMMON", "GRAMMAR.ARTICLES", "VOCABULARY.OTHER");
        assertThat(mistakes.records.stream().filter(r -> r.subcategory().equals("GRAMMAR.ARTICLES")))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.unitRefs()).containsExactly("ROUNDUP5.U10");
                    assertThat(r.correction()).isEqualTo("in the library");
                });
        assertThat(memory.pending).hasSize(2);
        assertThat(mistakes.opportunities).hasSize(2 + 5);
        assertThat(models.models.get(user).mistakeStats()).hasSize(3);
        assertThat(results.rows).hasSize(2);
        assertThat(publications.windows).containsKey(user);

        assertThat(learner.publishDue(Fakes.NOW)).isZero();
        assertThat(learner.publishDue(Fakes.NOW.plus(Duration.ofMinutes(5)))).isEqualTo(1);
        assertThat(learnerEvents.published).hasSize(1);
        assertThat(publications.windows).isEmpty();
    }

    @Test
    @DisplayName("goal, language and bands come from profile and assessment events")
    void goalAndBands() {
        learner.goalChanged(
                user, 4.5, LocalDate.parse("2027-01-20"), "Europe/Minsk", ExplanationLanguage.EN, Cause.none());
        learner.languageChanged(user, ExplanationLanguage.RU, Cause.none());
        learner.bandsUpdated(user, Map.of("OVERALL", 4.0), Cause.none());
        assertThat(learner.get(user).language()).isEqualTo(ExplanationLanguage.RU);
        assertThat(learner.get(user).bands()).containsEntry("OVERALL", 4.0);
        assertThat(learner.get(user).targetBand()).isEqualTo(4.5);
        assertThat(learner.get(UUID.randomUUID()).version()).isZero();
    }

    @Test
    @DisplayName("memory embeddings are filled by the job and counted as failures while AI is down")
    void memoryEmbeddings() {
        evaluatedEmail();
        MemoryService service = new MemoryService(memory, ai);
        ai.embeddingsDown = true;
        assertThat(service.embedPending(10)).isZero();
        assertThat(memory.failures).isEqualTo(1);
        ai.embeddingsDown = false;
        assertThat(service.embedPending(10)).isEqualTo(1);
        assertThat(service.embedPending(10)).isZero();
    }

    ClosedWeek week(int done) {
        return new ClosedWeek(
                user,
                UUID.randomUUID(),
                3,
                LocalDate.parse("2026-11-15"),
                LocalDate.parse("2026-11-21"),
                Fakes.NOW,
                15,
                done,
                done * 100.0 / 15,
                List.of(new ClosedWeek.Section("WRITING", 3, 3, 40, 40)),
                null,
                null);
    }

    static Map<String, Object> answer(int count, String wrong) {
        return Map.of(
                "headline",
                "Week 3: steady work",
                "summary",
                "You finished 12 of 15 tasks.",
                "topMistakes",
                List.of(Map.of(
                        "subcategory", "GRAMMAR.ARTICLES",
                        "count", count,
                        "pattern", "Missing articles",
                        "example", Map.of("wrong", wrong, "right", "in the library"),
                        "advice", "Study Round-Up Unit 10.")),
                "strengths",
                List.of("All Writing tasks done"),
                "nextWeekFocus",
                List.of(Map.of("subcategory", "GRAMMAR.ARTICLES", "why", "Your most frequent mistake")),
                "motivation",
                "Keep going.");
    }

    @Test
    @DisplayName("the review is worded by AI, retried once with the violations and published (AC-18.01.1)")
    void reviewRetriesThenPublishes() {
        evaluatedEmail();
        ai.answer(answer(9, "in library"));
        ai.answer(answer(1, "in library"));
        ClosedWeek week = week(12);
        weekly.weekCompleted(week);
        WeeklyReview review = reviews.byWeek.get(week.weekId());
        assertThat(review.aiFallback()).isFalse();
        assertThat(review.text().topMistakes().getFirst().unitRefs()).containsExactly("ROUNDUP5.U10");
        assertThat(ai.calls).hasSize(2);
        assertThat(ai.calls.get(1).variables().get("violations").toString())
                .contains("count of GRAMMAR.ARTICLES must be 1");
        assertThat(reviewEvents.published).hasSize(1);
        assertThat(models.snapshots).containsKey(user + ":3");
        assertThat(weekly.get(user, review.id())).contains(review);
        assertThat(weekly.list(user, null, null, 10)).hasSize(1);
        assertThat(weekly.list(user, week.weekId(), null, 10)).hasSize(1);
    }

    @Test
    @DisplayName("two invalid answers give the template review with aiFallback")
    void reviewFallsBackToTemplate() {
        evaluatedEmail();
        ai.answer(answer(9, "made up"));
        ai.answer(answer(9, "made up"));
        ClosedWeek week = week(12);
        weekly.weekCompleted(week);
        assertThat(reviews.byWeek.get(week.weekId()).aiFallback()).isTrue();
        assertThat(reviews.byWeek.get(week.weekId()).text().topMistakes()).hasSize(2);
    }

    @Test
    @DisplayName("fewer than 3 done tasks give the short review without AI (AC-18.01.5)")
    void lowActivity() {
        ClosedWeek week = week(2);
        weekly.weekCompleted(week);
        assertThat(ai.calls).isEmpty();
        assertThat(reviews.byWeek.get(week.weekId()).lowActivity()).isTrue();
    }

    @Test
    @DisplayName("unavailable AI defers the review; it is generated later (AC-18.01.4)")
    void deferredReview() {
        evaluatedEmail();
        ClosedWeek week = week(12);
        weekly.weekCompleted(week);
        assertThat(reviews.byWeek).isEmpty();
        assertThat(deferred.rows.get(week.weekId()).next()).isEqualTo(Fakes.NOW.plus(Duration.ofMinutes(20)));
        assertThat(weekly.retryDeferred(Fakes.NOW)).isZero();
        ai.answer(answer(1, "in library"));
        assertThat(weekly.retryDeferred(Fakes.NOW.plus(Duration.ofMinutes(20)))).isEqualTo(1);
        assertThat(reviews.byWeek).containsKey(week.weekId());
        assertThat(deferred.rows).isEmpty();
    }

    @Test
    @DisplayName("after 12 attempts the template review is stored")
    void deferredGivesUp() {
        evaluatedEmail();
        ClosedWeek week = week(12);
        weekly.generate(week, WeeklyReviewService.MAX_ATTEMPTS - 1);
        assertThat(reviews.byWeek.get(week.weekId()).aiFallback()).isTrue();
    }

    @Test
    @DisplayName("without the WEEKLY_REVIEW right there is no review and reading it is refused")
    void noRight() {
        rights.denied.add("WEEKLY_REVIEW");
        weekly.weekCompleted(week(12));
        assertThat(reviews.byWeek).isEmpty();
        assertThatThrownBy(() -> weekly.list(user, null, null, 5)).isInstanceOf(EntitlementRequiredException.class);
        assertThat(Severity.CLOSED_ITEM.weight()).isEqualTo(1.0);
    }
}
