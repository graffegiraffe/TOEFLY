package com.toefly.planning.application.service;

import com.toefly.planning.application.port.out.ItemSetCatalogPort;
import com.toefly.planning.application.port.out.LearnerSignalPort;
import com.toefly.planning.application.port.out.UnitCatalogPort;
import com.toefly.planning.application.port.out.WeekRepository;
import com.toefly.planning.domain.composition.CompositionRequest;
import com.toefly.planning.domain.composition.CompositionRules;
import com.toefly.planning.domain.composition.UnitRef;
import com.toefly.planning.domain.composition.WeakTypes;
import com.toefly.planning.domain.composition.WeekComposer;
import com.toefly.planning.domain.plan.StudyPlan;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekSection;
import com.toefly.planning.domain.week.WeekTask;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Composes and publishes the next week of a plan from the signals of the last two weeks (§10.2, §10.7). */
@Component
class WeekPlanner {

    static final Duration SIGNAL_WINDOW = Duration.ofDays(14);
    static final int ROTATION_WEEKS = 4;

    private final WeekRepository weeks;
    private final ItemSetCatalogPort catalog;
    private final UnitCatalogPort units;
    private final LearnerSignalPort signals;
    private final CompositionRules rules;
    private final PlanningSettings settings;

    WeekPlanner(
            WeekRepository weeks,
            ItemSetCatalogPort catalog,
            UnitCatalogPort units,
            LearnerSignalPort signals,
            CompositionRules rules,
            PlanningSettings settings) {
        this.weeks = weeks;
        this.catalog = catalog;
        this.units = units;
        this.signals = signals;
        this.rules = rules;
        this.settings = settings;
    }

    Week compose(StudyPlan plan, int weekNo, LocalDate day1, Instant now) {
        Map<WeekSection, Double> theta = signals.theta(plan.userId());
        List<TaskType> weak =
                WeakTypes.detect(signals.accuracySince(plan.userId(), now.minus(SIGNAL_WINDOW)), theta, plan.targets())
                        .stream()
                        .filter(settings.availableTypes()::contains)
                        .toList();
        Set<String> assigned = weeks.assignedRefs(plan.userId());
        CatalogSetSupply supply = new CatalogSetSupply(
                catalog,
                settings.examFormat(),
                assigned,
                weeks.doneItemSets(plan.userId()),
                theta,
                Set.copyOf(weak),
                plan.userId().getMostSignificantBits() ^ weekNo);
        CompositionRequest request = new CompositionRequest(
                weekNo,
                plan.minutesPerDay(),
                weak,
                settings.availableTypes(),
                settings.vocabularyReview(),
                settings.mockExam() && plan.mockExamWeek(weekNo),
                next(units.roundUp(), assigned),
                next(units.collinsGrammar(), assigned),
                next(units.collinsVocabulary(), assigned),
                settings.resources(),
                weeks.typeCounts(plan.id(), Math.max(1, weekNo - ROTATION_WEEKS)),
                supply);
        List<WeekTask> tasks = WeekComposer.compose(request, rules);
        return Week.publish(
                plan.id(),
                plan.userId(),
                weekNo,
                day1,
                plan.minutesPerDay(),
                plan.mockExamWeek(weekNo),
                weak,
                rationale(weak),
                tasks,
                now);
    }

    /** The first unit of a track that the learner has not had yet. */
    private static Optional<UnitRef> next(List<UnitRef> track, Set<String> assigned) {
        return track.stream()
                .filter(unit -> !assigned.contains(unit.unitId().toString()))
                .findFirst();
    }

    /** Why the week looks like this (Week.rationale); the agent writes it from phase 5. */
    static String rationale(List<TaskType> weak) {
        if (weak.isEmpty()) {
            return "The standard week for your daily time: every exam task type, the next book units and two resources.";
        }
        return "The standard week with more practice on "
                + weak.stream().map(TaskType::lowerLabel).collect(Collectors.joining(", "))
                + ": under 60% correct in the last two weeks.";
    }
}
