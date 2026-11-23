package com.toefly.planning.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.planning.application.port.in.OnboardingGoal;
import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.application.port.out.ItemSetCatalogPort.CatalogEntry;
import com.toefly.planning.domain.week.ExtraTaskDay;
import com.toefly.planning.domain.week.TaskOrigin;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekRuleException;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskKind;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@code addWeekTaskInternal} for {@code assignExtraTask} of Toefly Tutor (docs/06-ai-agent.md §4.5). */
@DisplayName("extra tasks of Toefly Tutor")
class AgentTaskScenarioTest {

    static final Instant START = Instant.parse("2026-11-10T09:00:00Z");

    private final Fakes fakes = new Fakes(START);
    private UUID user;
    private Week week;

    @BeforeEach
    void plan() {
        user = UUID.randomUUID();
        fakes.planService.onboardingCompleted(
                new OnboardingGoal(user, 1, null, ZoneId.of("Europe/Minsk"), 60, Map.of("OVERALL", 4.5)), Cause.none());
        week = fakes.weeks
                .findByPlan(fakes.plans.findByUser(user).orElseThrow().id())
                .getFirst();
    }

    private UUID freshSet(TaskType type, double minutes) {
        UUID id = UUID.randomUUID();
        fakes.catalog.published(
                new CatalogEntry(id, 1, type, "TOEFL_2026", 0, minutes, "articles in context", null), START);
        return id;
    }

    private void assertRule(Runnable command, String code) {
        assertThatThrownBy(command::run)
                .isInstanceOf(WeekRuleException.class)
                .satisfies(e -> assertThat(((WeekRuleException) e).code()).isEqualTo(code));
    }

    @Test
    @DisplayName("the set of the catalog lands on the first day with room, with the reason and the agent marker")
    void adds() {
        UUID set = freshSet(TaskType.READ_DAILY_LIFE, 2.5);

        WeekTask task =
                fakes.agentTasks.add(week.id(), set, ExtraTaskDay.NEXT_FREE_DAY, "  More practice on articles  ");

        Week saved = fakes.weeks.findById(week.id()).orElseThrow();
        assertThat(saved.task(task.id())).isSameAs(task);
        assertThat(task.origin()).isEqualTo(TaskOrigin.TUTOR_AGENT);
        assertThat(task.rationale()).isEqualTo("More practice on articles");
        assertThat(task.kind()).isEqualTo(WeekTaskKind.ITEM_SET);
        assertThat(task.title()).isEqualTo("Read in Daily Life: Articles in context");
        assertThat(task.repeat()).isFalse();
        assertThat(task.dayNo()).isEqualTo(1);
        assertThat(saved.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("TOMORROW is day 2; after day 7 the extension days count, a day past the week is refused")
    void days() {
        WeekTask tomorrow = fakes.agentTasks.add(
                week.id(), freshSet(TaskType.LISTEN_CONVERSATION, 2), ExtraTaskDay.TOMORROW, "Listening warm-up");
        assertThat(tomorrow.dayNo()).isEqualTo(2);

        fakes.clock.advance(Duration.ofDays(9));
        WeekTask today = fakes.agentTasks.add(
                week.id(), freshSet(TaskType.LISTEN_CONVERSATION, 2), ExtraTaskDay.TODAY, "Catch up");
        assertThat(today.dayNo()).isEqualTo(10);
        assertRule(
                () -> fakes.agentTasks.add(
                        week.id(), freshSet(TaskType.LISTEN_CONVERSATION, 2), ExtraTaskDay.TOMORROW, "x"),
                "validation-failed");
    }

    @Test
    @DisplayName("at most 3 tasks of the agent a week, never the same set twice")
    void limits() {
        UUID first = freshSet(TaskType.COMPLETE_THE_WORDS, 3);
        fakes.agentTasks.add(week.id(), first, ExtraTaskDay.NEXT_FREE_DAY, "One");
        assertRule(
                () -> fakes.agentTasks.add(week.id(), first, ExtraTaskDay.NEXT_FREE_DAY, "Again"),
                "item-set-already-in-week");
        fakes.agentTasks.add(week.id(), freshSet(TaskType.COMPLETE_THE_WORDS, 3), ExtraTaskDay.NEXT_FREE_DAY, "Two");
        fakes.agentTasks.add(week.id(), freshSet(TaskType.COMPLETE_THE_WORDS, 3), ExtraTaskDay.NEXT_FREE_DAY, "Three");
        assertRule(
                () -> fakes.agentTasks.add(
                        week.id(), freshSet(TaskType.COMPLETE_THE_WORDS, 3), ExtraTaskDay.NEXT_FREE_DAY, "Four"),
                "extra-task-limit-reached");
    }

    @Test
    @DisplayName("the day budget: no day keeps 1.25 × the daily minutes with a long set")
    void budget() {
        assertRule(
                () -> fakes.agentTasks.add(
                        week.id(), freshSet(TaskType.READ_ACADEMIC_PASSAGE, 80), ExtraTaskDay.NEXT_FREE_DAY, "Long"),
                "day-budget-exceeded");
    }

    @Test
    @DisplayName("a closed week, an unknown week or a hidden set are refused; a set done before is a repeat")
    void refusals() {
        UUID hidden = freshSet(TaskType.READ_DAILY_LIFE, 2.5);
        fakes.catalog.hidden(hidden, START);
        assertRule(() -> fakes.agentTasks.add(week.id(), hidden, ExtraTaskDay.TODAY, "x"), "not-found");
        assertRule(
                () -> fakes.agentTasks.add(
                        UUID.randomUUID(), freshSet(TaskType.READ_DAILY_LIFE, 2.5), ExtraTaskDay.TODAY, "x"),
                "not-found");

        WeekTask done = week.tasks().stream()
                .filter(task -> task.kind() == WeekTaskKind.ITEM_SET)
                .findFirst()
                .orElseThrow();
        fakes.attempts.attemptScored(
                user, done.id(), UUID.randomUUID(), done.taskType(), 3, 3, true, Instant.now(), Cause.none());
        Week next = fakes.weeks.findById(week.id()).orElseThrow();
        for (WeekTask task : next.tasks()) {
            if (task.kind() == WeekTaskKind.ITEM_SET) {
                fakes.attempts.attemptScored(
                        user, task.id(), UUID.randomUUID(), task.taskType(), 3, 3, true, Instant.now(), Cause.none());
            } else if (task.kind().manuallyCompletable()) {
                fakes.tasks.complete(user, task.id());
            }
        }
        fakes.tasks.finishEarly(user, week.id());
        assertRule(
                () -> fakes.agentTasks.add(week.id(), freshSet(TaskType.READ_DAILY_LIFE, 2.5), ExtraTaskDay.TODAY, "x"),
                "week-not-editable");

        Week week2 = fakes.weeks.findByPlan(week.planId()).stream()
                .filter(found -> found.weekNo() == 2)
                .findFirst()
                .orElseThrow();
        WeekTask repeat = fakes.agentTasks.add(
                week2.id(), UUID.fromString(done.refId()), ExtraTaskDay.NEXT_FREE_DAY, "Try it again");
        assertThat(repeat.repeat()).isTrue();
        assertThat(Set.of(repeat.status().name())).containsExactly("PENDING");
    }
}
