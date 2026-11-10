package com.toefly.planning.domain.composition;

import static com.toefly.planning.domain.composition.Requests.ALL;
import static com.toefly.planning.domain.composition.Requests.CLOSED;
import static com.toefly.planning.domain.composition.Requests.request;
import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.planning.domain.week.TaskOrigin;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.WeekSection;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskKind;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The standard composition of a week (docs/02 §4.0, docs/03 §10.2). */
class WeekComposerTest {

    static final CompositionRules RULES = CompositionRules.standard();

    private static double total(List<WeekTask> tasks) {
        return tasks.stream().mapToDouble(WeekTask::estimatedMinutes).sum();
    }

    private static Map<TaskType, Long> counts(List<WeekTask> tasks) {
        return tasks.stream()
                .filter(task -> task.taskType() != null)
                .collect(Collectors.groupingBy(WeekTask::taskType, Collectors.counting()));
    }

    @ParameterizedTest(name = "{0} minutes a day")
    @ValueSource(ints = {30, 45, 60, 90, 120})
    @DisplayName("INV-WK-08: the week takes 90–110% of the daily minutes × 7 and every type at least once")
    void budget(int minutes) {
        List<WeekTask> tasks = WeekComposer.compose(request(2, minutes, List.of(), ALL, false), RULES);
        assertThat(total(tasks)).isBetween(minutes * 7 * 0.9, minutes * 7 * 1.1);
        Map<TaskType, Long> counts = counts(tasks);
        for (TaskType type : TaskType.EXAM) {
            assertThat(counts.getOrDefault(type, 0L)).as(type.name()).isBetween(1L, 14L);
        }
    }

    @ParameterizedTest(name = "{0} minutes a day")
    @ValueSource(ints = {60, 90, 120, 180})
    @DisplayName("AC-04.03.1: no type has more than 14 sets; a long day stays under the budget when sets run out")
    void capPerType(int minutes) {
        for (var types : List.of(CLOSED, ALL)) {
            List<WeekTask> tasks = WeekComposer.compose(request(2, minutes, List.of(), types, false), RULES);
            assertThat(counts(tasks).values())
                    .allSatisfy(count -> assertThat(count).isLessThanOrEqualTo(14));
            assertThat(total(tasks)).isLessThanOrEqualTo(minutes * 7 * 1.1);
        }
    }

    @Test
    @DisplayName("this release: only closed types, and the 60-minute week still reaches 90%")
    void closedOnly() {
        List<WeekTask> tasks = WeekComposer.compose(request(2, 60, List.of(), CLOSED, false), RULES);
        assertThat(total(tasks)).isBetween(60 * 7 * 0.9, 60 * 7 * 1.1);
    }

    @Test
    @DisplayName("docs/02 §4.0: at 60 minutes the week has the base counts, the book units and two resources")
    void baseWeek() {
        List<WeekTask> tasks = WeekComposer.compose(request(1, 60, List.of(), CLOSED, false), RULES);
        Map<TaskType, Long> counts = counts(tasks);
        assertThat(counts.get(TaskType.COMPLETE_THE_WORDS)).isGreaterThanOrEqualTo(3);
        assertThat(counts.get(TaskType.READ_ACADEMIC_PASSAGE)).isGreaterThanOrEqualTo(2);
        assertThat(tasks)
                .filteredOn(task -> task.kind() == WeekTaskKind.GRAMMAR_UNIT)
                .hasSize(2);
        assertThat(tasks)
                .filteredOn(task -> task.kind() == WeekTaskKind.VOCABULARY_UNIT)
                .hasSize(1);
        assertThat(tasks)
                .filteredOn(task -> task.kind() == WeekTaskKind.EXTERNAL_RESOURCE)
                .hasSize(2);
        assertThat(tasks)
                .noneMatch(task -> task.taskType() != null && task.taskType().open());
        assertThat(tasks).allSatisfy(task -> assertThat(task.rationale()).isNotBlank());
    }

    @Test
    @DisplayName("docs/02 §4.0: a weak type gets ×1.5 sets, rounded up, marked as added by the agent")
    void weakType() {
        List<WeekTask> tasks =
                WeekComposer.compose(request(2, 60, List.of(TaskType.READ_ACADEMIC_PASSAGE), CLOSED, false), RULES);
        List<WeekTask> passages = tasks.stream()
                .filter(task -> task.taskType() == TaskType.READ_ACADEMIC_PASSAGE)
                .toList();
        assertThat(passages).hasSizeGreaterThanOrEqualTo(3);
        assertThat(passages).anySatisfy(task -> {
            assertThat(task.origin()).isEqualTo(TaskOrigin.AI);
            assertThat(task.rationale()).contains("weak type");
        });
    }

    @Test
    @DisplayName("docs/02 §4.0: at 15 minutes one task per section rotates; Round-Up every other week")
    void minimalMode() {
        List<WeekTask> week1 = WeekComposer.compose(request(1, 15, List.of(), CLOSED, false), RULES);
        List<WeekTask> week2 = WeekComposer.compose(request(2, 15, List.of(), CLOSED, false), RULES);
        assertThat(week1)
                .filteredOn(task -> task.kind() == WeekTaskKind.GRAMMAR_UNIT)
                .hasSize(1);
        assertThat(week2)
                .filteredOn(task -> task.kind() == WeekTaskKind.GRAMMAR_UNIT)
                .isEmpty();
        assertThat(week1)
                .filteredOn(task -> task.kind() == WeekTaskKind.VOCABULARY_UNIT)
                .isEmpty();
        TaskType reading1 = week1.stream()
                .filter(task -> task.section() == WeekSection.READING && task.origin() == TaskOrigin.SYSTEM)
                .findFirst()
                .orElseThrow()
                .taskType();
        TaskType reading2 = week2.stream()
                .filter(task -> task.section() == WeekSection.READING && task.origin() == TaskOrigin.SYSTEM)
                .findFirst()
                .orElseThrow()
                .taskType();
        assertThat(reading1).isNotEqualTo(reading2);
        assertThat(total(week2)).isBetween(15 * 7 * 0.9, 15 * 7 * 1.1);
    }

    @Test
    @DisplayName("docs/02 §4.0: a mock exam week takes 90 minutes from the other tasks")
    void mockExam() {
        List<WeekTask> plain = WeekComposer.compose(request(4, 60, List.of(), CLOSED, false), RULES);
        List<WeekTask> mock = WeekComposer.compose(request(4, 60, List.of(), CLOSED, true), RULES);
        assertThat(mock)
                .filteredOn(task -> task.kind() == WeekTaskKind.MOCK_EXAM)
                .hasSize(1);
        double setsPlain = plain.stream()
                .filter(t -> t.kind() == WeekTaskKind.ITEM_SET)
                .mapToDouble(WeekTask::estimatedMinutes)
                .sum();
        double setsMock = mock.stream()
                .filter(t -> t.kind() == WeekTaskKind.ITEM_SET)
                .mapToDouble(WeekTask::estimatedMinutes)
                .sum();
        assertThat(setsMock).isLessThan(setsPlain);
        assertThat(total(mock)).isLessThanOrEqualTo(60 * 7 * 1.1);
    }

    @Test
    @DisplayName("AC-04.01.4: without fresh sets a done set comes back as a repeat")
    void repeats() {
        CompositionRequest base = request(3, 60, List.of(), EnumSet.of(TaskType.READ_DAILY_LIFE), false);
        CompositionRequest request = new CompositionRequest(
                base.weekNo(),
                base.minutesPerDay(),
                base.weakTypes(),
                base.availableTypes(),
                false,
                false,
                base.roundUpUnit(),
                base.collinsGrammarUnit(),
                base.collinsVocabularyUnit(),
                base.resources(),
                Map.of(),
                new FakeSupply(3, true));
        List<WeekTask> tasks = WeekComposer.compose(request, RULES);
        assertThat(tasks)
                .filteredOn(task -> task.taskType() == TaskType.READ_DAILY_LIFE)
                .hasSize(3)
                .allSatisfy(task -> {
                    assertThat(task.repeat()).isTrue();
                    assertThat(task.rationale()).startsWith("Repeat");
                });
    }

    @Test
    @DisplayName("the smallest week may stay over the budget: units and the mock exam are never cut")
    void incompressible() {
        CompositionRequest base = request(1, 30, List.of(), Set.of(), true);
        List<WeekTask> tasks = WeekComposer.compose(base, RULES);
        assertThat(tasks).anyMatch(task -> task.kind() == WeekTaskKind.MOCK_EXAM);
        assertThat(tasks)
                .filteredOn(task -> task.title().startsWith("Round-Up"))
                .hasSize(1);
        assertThat(tasks)
                .filteredOn(task -> task.kind() == WeekTaskKind.EXTERNAL_RESOURCE)
                .hasSize(1);
        assertThat(Optional.of(total(tasks))).get().isEqualTo(90 + 60 + 15.0 + 60);
    }
}
