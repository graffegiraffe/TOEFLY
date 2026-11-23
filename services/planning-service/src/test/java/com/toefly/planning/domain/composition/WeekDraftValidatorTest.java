package com.toefly.planning.domain.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.planning.domain.composition.DraftLayout.Day;
import com.toefly.planning.domain.composition.DraftLayout.Pick;
import com.toefly.planning.domain.week.TaskOrigin;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.WeekSection;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskKind;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The validation of a draft of WeekGenerator (docs/06-ai-agent.md §5.2) on a small hand-made pool. */
@DisplayName("the validation of a WeekGenerator draft")
class WeekDraftValidatorTest {

    /** A Monday; days are MON…SUN. */
    static final LocalDate DAY1 = LocalDate.of(2026, 11, 23);

    static final int MINUTES = 30;
    static final double BUDGET = MINUTES * 7 * 1.1;
    static final List<String> NAMES = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    private static WeekTask set(TaskType type, double minutes) {
        return WeekTask.pending(
                WeekTaskKind.ITEM_SET,
                type.section(),
                type,
                java.util.UUID.randomUUID().toString(),
                type.label(),
                minutes,
                TaskOrigin.SYSTEM,
                "Standard week",
                false);
    }

    /** c01: a 60-minute unit; c02…c13: Reading and Listening of 15 minutes in turn; c14: an alternative passage. */
    static CandidatePool pool() {
        List<WeekCandidate> candidates = new ArrayList<>();
        candidates.add(new WeekCandidate(
                "c01",
                WeekTask.pending(
                        WeekTaskKind.GRAMMAR_UNIT,
                        WeekSection.GRAMMAR,
                        null,
                        "unit-1",
                        "Round-Up Unit 1",
                        60,
                        TaskOrigin.SYSTEM,
                        "Next unit of New Round-Up 5",
                        false),
                true,
                true,
                List.of()));
        for (int i = 2; i <= 13; i++) {
            TaskType type = i % 2 == 0 ? TaskType.READ_ACADEMIC_PASSAGE : TaskType.LISTEN_ACADEMIC_TALK;
            candidates.add(new WeekCandidate(CandidateCollector.ref(i), set(type, 15), false, true, List.of()));
        }
        candidates.add(new WeekCandidate("c14", set(TaskType.READ_ACADEMIC_PASSAGE, 15), false, false, List.of()));
        return new CandidatePool(
                candidates,
                candidates.stream()
                        .filter(WeekCandidate::baseline)
                        .map(WeekCandidate::task)
                        .toList());
    }

    /** MON: the unit alone; TUE…SUN: one passage and one talk. */
    static List<List<String>> valid() {
        List<List<String>> days = new ArrayList<>();
        days.add(new ArrayList<>(List.of("c01")));
        for (int i = 2; i <= 13; i += 2) {
            days.add(new ArrayList<>(List.of(CandidateCollector.ref(i), CandidateCollector.ref(i + 1))));
        }
        return days;
    }

    static DraftLayout layout(List<List<String>> days) {
        List<Day> list = new ArrayList<>();
        for (int day = 0; day < days.size(); day++) {
            list.add(new Day(
                    NAMES.get(day),
                    days.get(day).stream()
                            .map(ref -> new Pick(ref, "Why " + ref))
                            .toList()));
        }
        return new DraftLayout(list, "Goal", "Because");
    }

    static List<String> violations(List<List<String>> days) {
        return WeekDraftValidator.validate(pool(), layout(days), DAY1, MINUTES, BUDGET);
    }

    @Test
    @DisplayName("a draft that keeps every rule is valid and gives tasks on their days with the texts of the model")
    void valid_draft() {
        CandidatePool pool = pool();
        DraftLayout layout = layout(valid());
        assertThat(WeekDraftValidator.validate(pool, layout, DAY1, MINUTES, BUDGET))
                .isEmpty();

        List<WeekTask> tasks = WeekDraftValidator.tasks(pool, layout, DAY1, MINUTES, BUDGET);
        assertThat(tasks).hasSize(13);
        assertThat(tasks)
                .filteredOn(task -> task.kind() == WeekTaskKind.GRAMMAR_UNIT)
                .singleElement()
                .satisfies(unit -> {
                    assertThat(unit.dayNo()).isEqualTo(1);
                    assertThat(unit.rationale()).isEqualTo("Why c01");
                });
    }

    @Test
    @DisplayName("an empty why-for-you keeps the template; a long one is cut to 140 characters")
    void texts() {
        CandidatePool pool = pool();
        List<Day> days = new ArrayList<>(layout(valid()).days());
        days.set(0, new Day("MON", List.of(new Pick("c01", " "))));
        days.set(1, new Day("TUE", List.of(new Pick("c02", "x".repeat(300)), new Pick("c03", "ok"))));
        List<WeekTask> tasks = WeekDraftValidator.tasks(pool, new DraftLayout(days, "", ""), DAY1, MINUTES, BUDGET);
        assertThat(tasks)
                .filteredOn(task -> task.dayNo() == 1)
                .extracting(WeekTask::rationale)
                .containsExactly("Next unit of New Round-Up 5");
        assertThat(tasks)
                .filteredOn(task -> task.dayNo() == 2 && task.rationale().startsWith("x"))
                .singleElement()
                .satisfies(task -> assertThat(task.rationale()).hasSize(140));
    }

    @Test
    @DisplayName("refs must exist, be unique and include every mandatory candidate; days must be study days")
    void refs() {
        List<List<String>> days = valid();
        days.get(0).clear();
        days.get(1).add("c02");
        days.get(2).add("c77");
        List<Day> list = new ArrayList<>(layout(days).days());
        list.add(new Day("TUE", List.of()));
        list.add(new Day("DAY8", List.of()));

        assertThat(WeekDraftValidator.validate(pool(), new DraftLayout(list, "", ""), DAY1, MINUTES, BUDGET))
                .contains(
                        "c02 is used more than once; use each ref once.",
                        "c77 is not a candidate; use only the refs of the list.",
                        "Mandatory c01 is missing; include every mandatory candidate.",
                        "Day TUE is listed twice; list each day once.",
                        "Day DAY8 is not a study day of this week.");
        assertThatThrownBy(() -> WeekDraftValidator.tasks(pool(), layout(days), DAY1, MINUTES, BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(WeekDraftValidator.validate(pool(), layout(List.of()), DAY1, MINUTES, BUDGET))
                .contains("The week has no tasks.");
    }

    @Test
    @DisplayName("±15% a day: an overloaded day, an empty day and a unit that does not stay alone break the rule")
    void days() {
        List<List<String>> days = valid();
        days.get(1).addAll(days.get(2));
        days.get(2).clear();
        assertThat(violations(days))
                .anyMatch(v -> v.startsWith("TUE has 60 minutes; keep each day between 26 and 35"))
                .anyMatch(v -> v.startsWith("WED has 0 minutes"));

        List<List<String>> stacked = valid();
        stacked.get(0).add("c14");
        assertThat(violations(stacked)).contains("MON has a task longer than 35 minutes; put it alone on its day.");
    }

    @Test
    @DisplayName("AC-04.03.1: at most 2 tasks of one type a day")
    void types() {
        List<List<String>> days = valid();
        days.get(1).add("c04");
        days.get(1).add("c14");
        days.get(2).remove("c04");
        assertThat(violations(days)).contains("TUE has 3 Academic passage tasks; at most 2 of one type a day.");
    }

    @Test
    @DisplayName("sections stay within ±0.07 of the week of the rules, the week within 90%…110%")
    void sectionsAndTotal() {
        List<List<String>> days = valid();
        for (int i = 1; i < days.size(); i++) {
            days.get(i).removeIf(ref -> Integer.parseInt(ref.substring(1)) % 2 == 1);
        }
        assertThat(violations(days))
                .contains("The week has 150 minutes; keep it at least 216.")
                .anyMatch(v -> v.startsWith("LISTENING has 0.00 of the minutes; keep it between"));
    }

    @Test
    @DisplayName("no Writing on the mock exam day, one vocabulary review a day")
    void mockAndReviews() {
        WeekTask mock = WeekTask.pending(
                WeekTaskKind.MOCK_EXAM, WeekSection.READING, null, null, "Mock exam", 20, TaskOrigin.SYSTEM, "", false);
        WeekTask email = set(TaskType.WRITE_EMAIL, 7);
        List<WeekCandidate> candidates = new ArrayList<>(List.of(
                new WeekCandidate("c01", mock, true, true, List.of()),
                new WeekCandidate("c02", email, false, true, List.of())));
        for (int i = 3; i <= 4; i++) {
            candidates.add(new WeekCandidate(
                    CandidateCollector.ref(i),
                    WeekTask.pending(
                            WeekTaskKind.VOCABULARY_REVIEW,
                            WeekSection.VOCABULARY,
                            null,
                            null,
                            "Vocabulary review",
                            5,
                            TaskOrigin.SYSTEM,
                            "",
                            false),
                    true,
                    true,
                    List.of()));
        }
        CandidatePool pool = new CandidatePool(
                candidates, candidates.stream().map(WeekCandidate::task).toList());
        DraftLayout layout = new DraftLayout(
                List.of(new Day(
                        "SAT",
                        List.of(new Pick("c01", ""), new Pick("c02", ""), new Pick("c03", ""), new Pick("c04", "")))),
                "",
                "");

        assertThat(WeekDraftValidator.validate(pool, layout, DAY1, 5, 100))
                .contains(
                        "SAT has the mock exam; move the Writing and Speaking tasks to another day.",
                        "SAT has 2 vocabulary reviews; at most one a day.");
    }

    @Test
    @DisplayName("day names follow day 1 of the week; the bounds leave a day to each book unit")
    void calendar() {
        assertThat(WeekDraftValidator.dayNo(LocalDate.of(2026, 11, 25), "WED")).isEqualTo(1);
        assertThat(WeekDraftValidator.dayNo(LocalDate.of(2026, 11, 25), "TUE")).isEqualTo(7);
        assertThat(WeekDraftValidator.dayNo(DAY1, "Monday")).isZero();
        double[] bounds = WeekDraftValidator.dayBounds(60, List.of(60.0, 60.0, 60.0, 30.0, 30.0, 30.0, 30.0));
        assertThat(bounds[0]).isEqualTo(25.5);
        assertThat(bounds[1]).isCloseTo(69.0, org.assertj.core.data.Offset.offset(1e-9));
    }
}
