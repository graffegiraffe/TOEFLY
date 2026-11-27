package com.toefly.planning.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.planning.adapter.out.client.AiGatewayWeekDraftAdapter;
import com.toefly.planning.application.port.in.DraftNextWeekUseCase.ReviewMistake;
import com.toefly.planning.application.port.in.DraftNextWeekUseCase.WeeklyReview;
import com.toefly.planning.application.port.in.OnboardingGoal;
import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.application.port.out.WeekDraftAiPort.DraftReply;
import com.toefly.planning.domain.composition.GreedyWeekPacker;
import com.toefly.planning.domain.composition.WeekFocus.FocusPoint;
import com.toefly.planning.domain.learner.LearnerModelView;
import com.toefly.planning.domain.learner.MistakeStat;
import com.toefly.planning.domain.plan.StudyPlan;
import com.toefly.planning.domain.week.DraftSource;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskKind;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * WeekGenerator on the weekly review (docs/06-ai-agent.md §5.2, docs/12 phase 5c decision 1). The answers of the
 * model are hand-written fixtures under {@code ai-fixtures/week-generator/}: the candidate pool of this scenario is
 * deterministic (60 minutes a day, the closed types, Week 2, a learner with recurring article mistakes).
 */
class WeekGeneratorScenarioTest {

    static final Instant START = Instant.parse("2026-11-10T09:00:00Z");
    static final UUID USER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    static final JsonMapper JSON = JsonMapper.builder().build();

    private final Fakes fakes = new Fakes(START);
    private Week week1;
    private WeeklyReview review;

    static DraftReply fixture(String name) {
        try (InputStream in = Objects.requireNonNull(
                WeekGeneratorScenarioTest.class.getResourceAsStream("/ai-fixtures/week-generator/" + name))) {
            return AiGatewayWeekDraftAdapter.reply(JSON.readTree(in));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void closeWeekOne() {
        fakes.planService.onboardingCompleted(
                new OnboardingGoal(USER, 1, null, ZoneId.of("Europe/Minsk"), 60, Map.of("OVERALL", 4.5)), Cause.none());
        week1 = week(1);
        for (WeekTask task : week1.tasks()) {
            if (task.kind() == WeekTaskKind.ITEM_SET) {
                fakes.attempts.attemptScored(
                        USER, task.id(), UUID.randomUUID(), task.taskType(), 3, 3, true, Instant.now(), Cause.none());
            } else {
                fakes.tasks.complete(USER, task.id());
            }
        }
        fakes.tasks.finishEarly(USER, week1.id());
        fakes.models.save(new LearnerModelView(
                USER,
                1,
                START,
                List.of(new MistakeStat("GRAMMAR", "ARTICLES", 2.0, "ACTIVE", 4, List.of("ROUNDUP5.U10"))),
                Map.of("WRITING", 3.5)));
        review = new WeeklyReview(
                UUID.randomUUID(),
                USER,
                week1.id(),
                1,
                false,
                List.of(new ReviewMistake("GRAMMAR.ARTICLES", 4)),
                List.of(new FocusPoint("GRAMMAR.ARTICLES", "Articles were your most frequent mistake.")));
    }

    private StudyPlan plan() {
        return fakes.plans.findByUser(USER).orElseThrow();
    }

    private Week week(int weekNo) {
        return fakes.weeks.findByPlan(plan().id()).stream()
                .filter(week -> week.weekNo() == weekNo)
                .findFirst()
                .orElseThrow();
    }

    private int review() {
        AtomicInteger commits = new AtomicInteger();
        fakes.drafts.weeklyReviewGenerated(review, new Cause(null, review.reviewId()), effect -> {
            commits.incrementAndGet();
            effect.run();
            return true;
        });
        return commits.get();
    }

    private long published(Week week) {
        return fakes.events.of("week-published").stream()
                .filter(event -> event.weekId().equals(week.id()))
                .count();
    }

    @Test
    @DisplayName("the week of the rules waits for the review; the valid AI draft replaces it with the same id")
    void validDraft() {
        Week rules = week(2);
        assertThat(rules.draft().source()).isEqualTo(DraftSource.RULES);
        fakes.ai.replies.add(fixture("scenario-valid.json"));

        assertThat(review()).isEqualTo(1);

        Week drafted = week(2);
        assertThat(drafted.id()).isEqualTo(rules.id());
        assertThat(drafted.day1()).isEqualTo(rules.day1());
        assertThat(drafted.draft().source()).isEqualTo(DraftSource.AI);
        assertThat(drafted.draft().reviewId()).isEqualTo(review.reviewId());
        assertThat(drafted.draft().focus()).containsExactly("GRAMMAR.ARTICLES");
        assertThat(drafted.draft().stamp()).isNotNull();
        assertThat(drafted.draft().stamp().model()).isEqualTo("openai/gpt-oss-120b");
        assertThat(drafted.rationale()).startsWith("Use a, an and the correctly");
        assertThat(drafted.tasks()).hasSize(43);
        assertThat(drafted.tasks()).extracting(WeekTask::rationale).doesNotContainNull();
        // the 4 mandatory candidates: the grammar practice on day 1 and the three book units alone on their days
        WeekTask practice = drafted.tasks().stream()
                .filter(task -> task.taskType() == TaskType.GRAMMAR_EXERCISE)
                .findFirst()
                .orElseThrow();
        assertThat(practice.rationale()).isEqualTo(GreedyWeekPacker.GRAMMAR_PRACTICE_WHY);
        assertThat(practice.title()).contains("articles", "ROUNDUP5.U10");
        assertThat(practice.dayNo()).isEqualTo(1);
        assertThat(drafted.tasks().stream().filter(task -> task.kind() == WeekTaskKind.GRAMMAR_UNIT))
                .extracting(WeekTask::dayNo)
                .containsExactlyInAnyOrder(2, 7);
        assertThat(published(drafted)).isEqualTo(2);

        var call = fakes.ai.calls.getFirst();
        assertThat(call.idempotencyKey()).isEqualTo("WeekGenerator:" + rules.id() + ":" + review.reviewId() + ":1");
        assertThat(call.variables())
                .containsEntry("weekNumber", 2)
                .containsEntry("mandatory", "c09, c59, c60, c61")
                .containsEntry("studyDays", "WED, THU, FRI, SAT, SUN, MON, TUE")
                .containsEntry("previousViolations", "");
        assertThat((String) call.variables().get("candidates"))
                .startsWith("{\"ref\":\"c01\",\"kind\":\"ITEM_SET\",\"section\":\"WRITING\"")
                .contains("\"skills\":[\"GRAMMAR.ARTICLES\"]", "\"mandatory\":true")
                .doesNotContain(rules.tasks().getFirst().id().toString());
        assertThat((String) call.variables().get("focus")).contains("GRAMMAR.ARTICLES: Articles were");
    }

    @Test
    @DisplayName("a broken draft gets one retry with its violations; the second draft is used")
    void retry() {
        fakes.ai.replies.add(fixture("scenario-broken.json"));
        fakes.ai.replies.add(fixture("scenario-valid.json"));

        review();

        assertThat(fakes.ai.calls).hasSize(2);
        assertThat((String) fakes.ai.calls.get(1).variables().get("previousViolations"))
                .contains(
                        "c99 is not a candidate",
                        "c01 is used more than once",
                        "Mandatory c09 is missing",
                        "Mandatory c59 is missing",
                        "WED has 126 minutes; keep each day between",
                        "The week has 132 minutes",
                        "GRAMMAR has 0.91 of the minutes");
        assertThat(fakes.ai.calls.get(1).idempotencyKey()).endsWith(":2");
        assertThat(week(2).draft().source()).isEqualTo(DraftSource.AI);
    }

    @Test
    @DisplayName("two broken drafts: GreedyWeekPacker with aiFallback = true and template why-for-you texts")
    void fallbackAfterTwoBrokenDrafts() {
        fakes.ai.replies.add(fixture("scenario-broken.json"));
        fakes.ai.replies.add(fixture("scenario-broken.json"));

        review();

        Week fallback = week(2);
        assertThat(fallback.draft().source()).isEqualTo(DraftSource.AI_FALLBACK);
        assertThat(fallback.draft().aiFallback()).isTrue();
        assertThat(fallback.draft().stamp()).isNull();
        assertThat(fallback.rationale()).startsWith("Focus of the week: articles.");
        assertThat(fallback.tasks())
                .filteredOn(task -> task.taskType() == TaskType.BUILD_A_SENTENCE)
                .allSatisfy(task -> assertThat(task.rationale()).startsWith("Focus of the week: articles"));
        assertThat(fallback.tasks())
                .filteredOn(task -> task.taskType() == TaskType.GRAMMAR_EXERCISE)
                .extracting(WeekTask::rationale)
                .containsExactly(GreedyWeekPacker.GRAMMAR_PRACTICE_WHY);
        assertThat(published(fallback)).isEqualTo(2);
    }

    @Test
    @DisplayName("ai-gateway down: no retry, the fallback week is published at once")
    void gatewayDown() {
        review();

        assertThat(fakes.ai.calls).hasSize(1);
        assertThat(week(2).draft().source()).isEqualTo(DraftSource.AI_FALLBACK);
    }

    @Test
    @DisplayName("the week is drafted once: a started week and a second review keep it as it is")
    void buildOnce() {
        fakes.ai.replies.add(fixture("scenario-valid.json"));
        review();
        Week drafted = week(2);
        int version = drafted.version();

        review = new WeeklyReview(UUID.randomUUID(), USER, week1.id(), 1, false, List.of(), review.nextWeekFocus());
        assertThat(review()).isEqualTo(1);

        assertThat(fakes.ai.calls).hasSize(1);
        assertThat(week(2).version()).isEqualTo(version);
    }

    @Test
    @DisplayName("a learner who already started the next week keeps it; the review is only acknowledged")
    void startedWeekStays() {
        Week rules = week(2);
        WeekTask first = rules.tasks().stream()
                .filter(task -> task.kind() == WeekTaskKind.ITEM_SET)
                .findFirst()
                .orElseThrow();
        fakes.attempts.attemptStarted(USER, first.id(), UUID.randomUUID(), false, Instant.now());

        assertThat(review()).isEqualTo(1);

        assertThat(fakes.ai.calls).isEmpty();
        assertThat(week(2).draft().source()).isEqualTo(DraftSource.RULES);
    }

    @Test
    @DisplayName("a review without a next week (the plan is over or unknown) changes nothing")
    void noNextWeek() {
        review = new WeeklyReview(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, true, List.of(), List.of());

        assertThat(review()).isEqualTo(1);
        assertThat(fakes.ai.calls).isEmpty();
    }

    @Test
    @DisplayName("AC-14.05.2: without a review, the week of the rules already adds the practice of a recurring topic")
    void rulesWeekFollowsLearnerModel() {
        Week week2 = week(2);
        // the model arrived after week 1 closed: the rules week 2 does not know it yet
        assertThat(week2.tasks()).noneMatch(task -> task.taskType() == TaskType.GRAMMAR_EXERCISE);

        for (WeekTask task : week2.tasks()) {
            if (task.kind() == WeekTaskKind.ITEM_SET) {
                fakes.attempts.attemptScored(
                        USER, task.id(), UUID.randomUUID(), task.taskType(), 3, 3, true, Instant.now(), Cause.none());
            } else {
                fakes.tasks.complete(USER, task.id());
            }
        }
        fakes.clock.advance(java.time.Duration.ofDays(1));
        fakes.tasks.finishEarly(USER, week2.id());

        Week week3 = week(3);
        assertThat(week3.draft().source()).isEqualTo(DraftSource.RULES);
        assertThat(week3.draft().focus()).containsExactly("GRAMMAR.ARTICLES");
        assertThat(week3.tasks())
                .filteredOn(task -> task.taskType() == TaskType.GRAMMAR_EXERCISE)
                .extracting(WeekTask::rationale)
                .containsExactly(GreedyWeekPacker.GRAMMAR_PRACTICE_WHY);
    }
}
