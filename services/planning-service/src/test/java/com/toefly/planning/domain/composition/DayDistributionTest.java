package com.toefly.planning.domain.composition;

import static com.toefly.planning.domain.composition.Requests.ALL;
import static com.toefly.planning.domain.composition.Requests.request;
import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** AC-04.03.1: days within ±25% of the daily minutes, at most two tasks of one type a day. */
class DayDistributionTest {

    static final LocalDate SATURDAY = LocalDate.of(2026, 11, 7);

    private static Week publish(int minutes, boolean mock) {
        List<WeekTask> tasks = WeekComposer.compose(
                request(4, minutes, List.of(TaskType.LISTEN_CONVERSATION), ALL, mock), CompositionRules.standard());
        return Week.publish(
                UUID.randomUUID(),
                UUID.randomUUID(),
                4,
                SATURDAY,
                minutes,
                mock,
                List.of(),
                null,
                tasks,
                Instant.parse("2026-11-07T08:00:00Z"));
    }

    private static String describe(Map<Integer, List<WeekTask>> days) {
        StringBuilder text = new StringBuilder();
        days.forEach((day, tasks) -> text.append("\n  day ")
                .append(day)
                .append(": ")
                .append(tasks.stream()
                        .map(task -> (task.taskType() == null
                                        ? task.kind().name()
                                        : task.taskType().name()) + " " + task.estimatedMinutes())
                        .collect(Collectors.joining(", "))));
        return text.toString();
    }

    private static Map<Integer, List<WeekTask>> byDay(Week week) {
        return week.tasks().stream().collect(Collectors.groupingBy(WeekTask::dayNo));
    }

    @ParameterizedTest(name = "{0} minutes a day")
    @ValueSource(ints = {60, 90, 120})
    @DisplayName("AC-04.03.1: with divisible tasks every day is within 75–125% of the daily minutes")
    void dayLoad(int minutes) {
        Week week = publish(minutes, false);
        Map<Integer, List<WeekTask>> days = byDay(week);
        assertThat(days.keySet()).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7);
        days.forEach((day, tasks) -> {
            double load = tasks.stream().mapToDouble(WeekTask::estimatedMinutes).sum();
            assertThat(load).as("day %d of %s", day, describe(days)).isBetween(0.75 * minutes, 1.25 * minutes);
        });
    }

    @ParameterizedTest(name = "{0} minutes a day")
    @ValueSource(ints = {15, 30, 45, 60, 90, 120, 180})
    @DisplayName("AC-04.03.1: at most two tasks of one type a day; an over-full day holds one indivisible task")
    void sameTypeAndIndivisible(int minutes) {
        Week week = publish(minutes, false);
        byDay(week).forEach((day, tasks) -> {
            Map<TaskType, Long> types = tasks.stream()
                    .filter(task -> task.taskType() != null)
                    .collect(Collectors.groupingBy(WeekTask::taskType, Collectors.counting()));
            assertThat(types.values()).allSatisfy(count -> assertThat(count).isLessThanOrEqualTo(2));
            double load = tasks.stream().mapToDouble(WeekTask::estimatedMinutes).sum();
            if (load > 1.25 * minutes) {
                assertThat(tasks)
                        .as("day " + day)
                        .anySatisfy(task -> assertThat(task.estimatedMinutes()).isGreaterThan(1.25 * minutes));
            }
        });
    }

    @Test
    @DisplayName("§10.3: the mock exam goes to a weekend day")
    void mockOnWeekend() {
        Week week = publish(90, true);
        WeekTask mock = week.tasks().stream()
                .filter(task -> task.kind() == WeekTaskKind.MOCK_EXAM)
                .findFirst()
                .orElseThrow();
        assertThat(week.date(mock.dayNo()).getDayOfWeek().getValue()).isIn(6, 7);
    }

    @Test
    @DisplayName("§10.3 step 5: the order inside a day puts Writing before Reading and units after them")
    void orderInsideDay() {
        Week week = publish(60, false);
        byDay(week).forEach((day, tasks) -> {
            List<WeekTask> sorted = tasks.stream()
                    .sorted(java.util.Comparator.comparingInt(WeekTask::sortOrder))
                    .toList();
            int lastRank = -1;
            for (WeekTask task : sorted) {
                int rank = switch (task.section()) {
                    case WRITING, SPEAKING -> 2;
                    case READING, LISTENING -> 3;
                    case GRAMMAR, VOCABULARY -> 4;
                    case RESOURCES -> 5;
                };
                assertThat(rank).isGreaterThanOrEqualTo(lastRank);
                lastRank = rank;
            }
        });
    }
}
