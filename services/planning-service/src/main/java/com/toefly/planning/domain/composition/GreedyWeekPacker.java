package com.toefly.planning.domain.composition;

import com.toefly.planning.domain.composition.WeekFocus.FocusPoint;
import com.toefly.planning.domain.learner.MistakeStat;
import com.toefly.planning.domain.week.TaskOrigin;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.WeekSection;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The deterministic week of the rules (docs/03 §10.2) with the focus of the week: the focus types count as weak
 * types, recurring grammar topics add AI practice (AC-14.05.2), and every task gets a template "why for you". It is
 * the week of a learner before the weekly review and the fallback of WeekGenerator (docs/06-ai-agent.md §5.2).
 */
public final class GreedyWeekPacker {

    /** The label of the AI practice for a recurring grammar topic (AC-14.05.2). */
    public static final String GRAMMAR_PRACTICE_WHY = "Because of mistakes in your writing";

    static final int WHY_LENGTH = 140;

    private GreedyWeekPacker() {}

    /**
     * Tasks of the week without days.
     *
     * @param grammarPractice the grammar practice among the tasks: task id to its mistake subcategory
     */
    public record Packed(List<WeekTask> tasks, Map<UUID, String> grammarPractice) {

        public Packed {
            tasks = List.copyOf(tasks);
            grammarPractice = Map.copyOf(grammarPractice);
        }
    }

    /** @param request the composition with the focus types already among the weak types */
    public static Packed pack(CompositionRequest request, CompositionRules rules, WeekFocus focus) {
        List<WeekTask> tasks = new ArrayList<>();
        for (WeekTask task : WeekComposer.compose(request, rules)) {
            tasks.add(focusWhy(task, focus).map(task::withRationale).orElse(task));
        }
        Map<UUID, String> practice = new HashMap<>();
        if (request.availableTypes().contains(TaskType.GRAMMAR_EXERCISE)) {
            for (MistakeStat topic : focus.grammarPractice()) {
                Optional<ItemSetCandidate> set = request.supply().next(TaskType.GRAMMAR_EXERCISE);
                if (set.isEmpty()) {
                    break; // the bank has no grammar exercise left; content-factory fills it from phase 9
                }
                WeekTask task = grammarPractice(set.get(), topic, rules);
                tasks.add(task);
                practice.put(task.id(), topic.subcategory());
            }
        }
        return new Packed(tasks, practice);
    }

    /** The template text of a task of a focus type: the focus point and its reason. */
    static Optional<String> focusWhy(WeekTask task, WeekFocus focus) {
        if (task.kind() != WeekTaskKind.ITEM_SET || task.taskType() == null || task.repeat()) {
            return Optional.empty();
        }
        return focus.pointFor(task.taskType()).map(GreedyWeekPacker::why);
    }

    static String why(FocusPoint point) {
        String why = "Focus of the week: " + point.label()
                + (point.why().isBlank() ? "" : " — " + point.why().strip());
        return cut(why);
    }

    static WeekTask grammarPractice(ItemSetCandidate set, MistakeStat topic, CompositionRules rules) {
        String units = topic.unitRefs().isEmpty() ? "" : " (" + String.join(", ", topic.unitRefs()) + ")";
        String title =
                TaskType.GRAMMAR_EXERCISE.label() + ": " + new FocusPoint(topic.subcategory(), "").label() + units;
        return WeekTask.pending(
                WeekTaskKind.ITEM_SET,
                WeekSection.GRAMMAR,
                TaskType.GRAMMAR_EXERCISE,
                set.itemSetId().toString(),
                title.length() > 200 ? title.substring(0, 200) : title,
                set.estimatedMinutes() > 0 ? set.estimatedMinutes() : rules.minutesOf(TaskType.GRAMMAR_EXERCISE),
                TaskOrigin.AI,
                GRAMMAR_PRACTICE_WHY,
                set.repeat());
    }

    static String cut(String text) {
        return text.length() <= WHY_LENGTH
                ? text
                : text.substring(0, WHY_LENGTH - 1).strip() + "…";
    }
}
