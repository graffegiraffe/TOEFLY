package com.toefly.planning.domain.composition;

import static com.toefly.planning.domain.composition.Requests.CLOSED;
import static com.toefly.planning.domain.composition.Requests.request;
import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.planning.domain.composition.WeekFocus.FocusPoint;
import com.toefly.planning.domain.learner.LearnerModelView;
import com.toefly.planning.domain.learner.MistakeStat;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskKind;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CandidateCollector, GreedyWeekPacker and the focus of the week (docs/06-ai-agent.md §5.2)")
class CandidateCollectorTest {

    static final CompositionRules RULES = CompositionRules.standard();

    static WeekFocus articles() {
        LearnerModelView model = new LearnerModelView(
                UUID.randomUUID(),
                2,
                Instant.EPOCH,
                List.of(
                        new MistakeStat("GRAMMAR", "ARTICLES", 2.0, "ACTIVE", 4, List.of("ROUNDUP5.U10")),
                        new MistakeStat("COMPREHENSION", "COMPREHENSION.LISTEN_DETAIL", 1.0, "ACTIVE", 2, List.of())),
                Map.of());
        return WeekFocus.of(List.of(new FocusPoint("GRAMMAR.ARTICLES", "Most frequent mistake.")), model);
    }

    static CompositionRequest withFocus(WeekFocus focus, Set<TaskType> types) {
        CompositionRequest base = request(2, 60, List.of(), types, false);
        return new CompositionRequest(
                base.weekNo(),
                base.weeksLeft(),
                base.minutesPerDay(),
                focus.types(types),
                base.availableTypes(),
                base.vocabularyReview(),
                base.mockExam(),
                base.roundUpUnit(),
                base.collinsGrammarUnit(),
                base.collinsVocabularyUnit(),
                base.resources(),
                base.recentTypeCounts(),
                base.supply());
    }

    @Test
    @DisplayName("every item-set type has 3 to 5 candidates, refs are c01…, the focus comes first")
    void pool() {
        WeekFocus focus = articles();
        CandidatePool pool = CandidateCollector.collect(withFocus(focus, CLOSED), RULES, focus);

        assertThat(pool.candidates()).extracting(WeekCandidate::ref).doesNotHaveDuplicates();
        assertThat(pool.candidates().getFirst().ref()).isEqualTo("c01");
        assertThat(pool.candidates().getFirst().skills()).containsExactly("GRAMMAR.ARTICLES");
        Map<TaskType, Long> perType = pool.candidates().stream()
                .filter(c ->
                        c.task().kind() == WeekTaskKind.ITEM_SET && c.task().taskType() != TaskType.GRAMMAR_EXERCISE)
                .collect(Collectors.groupingBy(c -> c.task().taskType(), Collectors.counting()));
        assertThat(perType).isNotEmpty();
        Map<TaskType, Long> baseline = pool.baseline().stream()
                .filter(task -> task.kind() == WeekTaskKind.ITEM_SET && task.taskType() != TaskType.GRAMMAR_EXERCISE)
                .collect(Collectors.groupingBy(WeekTask::taskType, Collectors.counting()));
        perType.forEach((type, count) ->
                assertThat(count - baseline.get(type)).as(type.name()).isBetween(1L, 2L));
        assertThat(perType.values()).allSatisfy(count -> assertThat(count).isGreaterThanOrEqualTo(3));
        assertThat(pool.candidates().stream()
                        .filter(c -> !c.baseline())
                        .map(c -> c.task().kind()))
                .contains(WeekTaskKind.ITEM_SET, WeekTaskKind.EXTERNAL_RESOURCE);
        assertThat(pool.baseline()).hasSize((int)
                pool.candidates().stream().filter(WeekCandidate::baseline).count());
        assertThat(pool.sectionWeights().values().stream()
                        .mapToDouble(Double::doubleValue)
                        .sum())
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("AC-14.05.2: the grammar practice is mandatory with its label, like the book units")
    void mandatory() {
        WeekFocus focus = articles();
        CandidatePool pool = CandidateCollector.collect(withFocus(focus, CLOSED), RULES, focus);

        List<WeekCandidate> mandatory =
                pool.candidates().stream().filter(WeekCandidate::mandatory).toList();
        assertThat(mandatory)
                .extracting(c -> c.task().kind())
                .containsOnly(WeekTaskKind.ITEM_SET, WeekTaskKind.GRAMMAR_UNIT, WeekTaskKind.VOCABULARY_UNIT);
        WeekCandidate practice = mandatory.stream()
                .filter(c -> c.task().taskType() == TaskType.GRAMMAR_EXERCISE)
                .findFirst()
                .orElseThrow();
        assertThat(practice.task().rationale()).isEqualTo(GreedyWeekPacker.GRAMMAR_PRACTICE_WHY);
        assertThat(practice.task().title()).isEqualTo("Grammar exercise: articles (ROUNDUP5.U10)");
        assertThat(practice.skills()).containsExactly("GRAMMAR.ARTICLES");
        assertThat(pool.mandatoryRefs()).contains(practice.ref());
    }

    @Test
    @DisplayName("GreedyWeekPacker: focus types get the focus text; no grammar exercise type, no practice")
    void packer() {
        WeekFocus focus = articles();
        Set<TaskType> noExercises = EnumSet.copyOf(CLOSED);
        noExercises.remove(TaskType.GRAMMAR_EXERCISE);
        GreedyWeekPacker.Packed packed = GreedyWeekPacker.pack(withFocus(focus, noExercises), RULES, focus);

        assertThat(packed.grammarPractice()).isEmpty();
        assertThat(packed.tasks())
                .filteredOn(task -> task.taskType() == TaskType.BUILD_A_SENTENCE)
                .hasSizeGreaterThanOrEqualTo(3)
                .allSatisfy(task ->
                        assertThat(task.rationale()).isEqualTo("Focus of the week: articles — Most frequent mistake."));
        assertThat(GreedyWeekPacker.cut("x".repeat(200))).hasSize(140).endsWith("…");
    }

    @Test
    @DisplayName("the focus: the review first, else the active mistakes; subcategories map to task types")
    void focus() {
        LearnerModelView model = new LearnerModelView(
                UUID.randomUUID(),
                1,
                Instant.EPOCH,
                List.of(
                        new MistakeStat("PRONUNCIATION", "TH", 1.0, "ACTIVE", 5, List.of()),
                        new MistakeStat("GRAMMAR", "PASSIVE", 0.5, "RESOLVED", 0, List.of())),
                Map.of());
        WeekFocus fromModel = WeekFocus.of(List.of(), model);
        assertThat(fromModel.points()).extracting(FocusPoint::subcategory).containsExactly("PRONUNCIATION.TH");
        assertThat(fromModel.types(EnumSet.allOf(TaskType.class)))
                .containsExactly(TaskType.LISTEN_AND_REPEAT, TaskType.TAKE_AN_INTERVIEW);
        assertThat(fromModel.types(CLOSED)).isEmpty();
        assertThat(WeekFocus.none().empty()).isTrue();
        assertThat(articles().codes()).containsExactly("GRAMMAR.ARTICLES");

        assertThat(WeekFocus.typesOf("VOCABULARY.IN_CONTEXT")).contains(TaskType.READ_ACADEMIC_PASSAGE);
        assertThat(WeekFocus.typesOf("VOCABULARY.COLLOCATION")).contains(TaskType.WRITE_ACADEMIC_DISCUSSION);
        assertThat(WeekFocus.typesOf("SPELLING.COMMON")).contains(TaskType.COMPLETE_THE_WORDS);
        assertThat(WeekFocus.typesOf("COHERENCE.TRANSITIONS")).contains(TaskType.WRITE_EMAIL);
        assertThat(WeekFocus.typesOf("COMPREHENSION.LISTEN_DETAIL")).contains(TaskType.LISTEN_ACADEMIC_TALK);
        assertThat(WeekFocus.typesOf("COMPREHENSION.INFERENCE")).contains(TaskType.READ_ACADEMIC_PASSAGE);
        assertThat(WeekFocus.typesOf("TIME_MANAGEMENT.PACING")).isEmpty();
        assertThat(new FocusPoint("TASK_RESPONSE.BULLET_MISSING", "").label()).isEqualTo("bullet missing");
        assertThat(WeekFocus.of(List.of(), model).skillsOf(TaskType.LISTEN_AND_REPEAT))
                .containsExactly("PRONUNCIATION.TH");
    }
}
