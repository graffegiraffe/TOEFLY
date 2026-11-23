package com.toefly.planning.application.service;

import com.toefly.planning.application.port.out.ItemSetCatalogPort;
import com.toefly.planning.application.port.out.LearnerModelPort;
import com.toefly.planning.application.port.out.LearnerSignalPort;
import com.toefly.planning.application.port.out.UnitCatalogPort;
import com.toefly.planning.application.port.out.WeekRepository;
import com.toefly.planning.domain.composition.CandidateCollector;
import com.toefly.planning.domain.composition.CandidatePool;
import com.toefly.planning.domain.composition.CompositionRequest;
import com.toefly.planning.domain.composition.CompositionRules;
import com.toefly.planning.domain.composition.GreedyWeekPacker;
import com.toefly.planning.domain.composition.UnitRef;
import com.toefly.planning.domain.composition.WeakTypes;
import com.toefly.planning.domain.composition.WeekFocus;
import com.toefly.planning.domain.composition.WeekFocus.FocusPoint;
import com.toefly.planning.domain.learner.LearnerModelView;
import com.toefly.planning.domain.plan.StudyPlan;
import com.toefly.planning.domain.week.DraftInfo;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekSection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Composes the weeks of a plan from the signals of the last two weeks and the focus of the learner model (§10.2,
 * §10.7), and collects the candidates of WeekGenerator (docs/06-ai-agent.md §5.2).
 */
@Component
class WeekPlanner {

    static final Duration SIGNAL_WINDOW = Duration.ofDays(14);
    static final int ROTATION_WEEKS = 4;

    private final WeekRepository weeks;
    private final ItemSetCatalogPort catalog;
    private final UnitCatalogPort units;
    private final LearnerSignalPort signals;
    private final LearnerModelPort models;
    private final CompositionRules rules;
    private final PlanningSettings settings;

    WeekPlanner(
            WeekRepository weeks,
            ItemSetCatalogPort catalog,
            UnitCatalogPort units,
            LearnerSignalPort signals,
            LearnerModelPort models,
            CompositionRules rules,
            PlanningSettings settings) {
        this.weeks = weeks;
        this.catalog = catalog;
        this.units = units;
        this.signals = signals;
        this.models = models;
        this.rules = rules;
        this.settings = settings;
    }

    /**
     * The composition of one week.
     *
     * @param weak weak types: the measured ones first, then the types of the focus
     * @param measured weak types by accuracy and θ only
     */
    record Composition(CompositionRequest request, List<TaskType> weak, List<TaskType> measured) {}

    /** The candidates of WeekGenerator for a week that is drafted again, and the budget of the week. */
    record Prepared(CandidatePool pool, List<TaskType> weak, List<TaskType> measured, double budgetMax) {}

    /**
     * The week of the rules, published at once: before the weekly review its focus comes from the learner model, so
     * recurring grammar mistakes already add their practice (AC-14.05.2).
     */
    Week compose(StudyPlan plan, int weekNo, LocalDate day1, Instant now) {
        WeekFocus focus = WeekFocus.of(List.of(), model(plan.userId()));
        Composition composition = composition(plan, weekNo, now, focus, weeks.assignedRefs(plan.userId()));
        return Week.publish(
                plan.id(),
                plan.userId(),
                weekNo,
                day1,
                plan.minutesPerDay(),
                plan.mockExamWeek(weekNo),
                composition.weak(),
                rationale(composition.measured(), focus),
                GreedyWeekPacker.pack(composition.request(), rules, focus).tasks(),
                now,
                DraftInfo.rules(focus.codes()));
    }

    /** The candidate pool of a week that a weekly review drafts again; its own sets count as free. */
    Prepared prepare(StudyPlan plan, Week week, WeekFocus focus, Instant now) {
        Composition composition =
                composition(plan, week.weekNo(), now, focus, weeks.assignedRefs(plan.userId(), week.id()));
        return new Prepared(
                CandidateCollector.collect(composition.request(), rules, focus),
                composition.weak(),
                composition.measured(),
                plan.minutesPerDay() * 7.0 * rules.budgetMaxFactor());
    }

    LearnerModelView model(UUID userId) {
        return models.find(userId).orElseGet(() -> LearnerModelView.empty(userId));
    }

    private Composition composition(StudyPlan plan, int weekNo, Instant now, WeekFocus focus, Set<String> assigned) {
        Map<WeekSection, Double> theta = signals.theta(plan.userId());
        List<TaskType> measured =
                WeakTypes.detect(signals.accuracySince(plan.userId(), now.minus(SIGNAL_WINDOW)), theta, plan.targets())
                        .stream()
                        .filter(settings.availableTypes()::contains)
                        .toList();
        List<TaskType> weak = new ArrayList<>(measured);
        for (TaskType type : focus.types(settings.availableTypes())) {
            if (!weak.contains(type)) {
                weak.add(type);
            }
        }
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
                plan.weeksTotal() - weekNo + 1,
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
        return new Composition(request, List.copyOf(weak), measured);
    }

    /** The first unit of a track that the learner has not had yet. */
    private static Optional<UnitRef> next(List<UnitRef> track, Set<String> assigned) {
        return track.stream()
                .filter(unit -> !assigned.contains(unit.unitId().toString()))
                .findFirst();
    }

    /** Why the week looks like this (Week.rationale) when WeekGenerator did not write it. */
    static String rationale(List<TaskType> weak, WeekFocus focus) {
        if (focus.points().isEmpty()) {
            return rationale(weak);
        }
        return "Focus of the week: "
                + focus.points().stream().map(FocusPoint::label).collect(Collectors.joining(", "))
                + ". "
                + rationale(weak);
    }

    static String rationale(List<TaskType> weak) {
        if (weak.isEmpty()) {
            return "The standard week for your daily time: every exam task type, the next book units and two resources.";
        }
        return "The standard week with more practice on "
                + weak.stream().map(TaskType::lowerLabel).collect(Collectors.joining(", "))
                + ": under 60% correct in the last two weeks.";
    }
}
