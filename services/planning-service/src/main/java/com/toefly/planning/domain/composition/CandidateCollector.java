package com.toefly.planning.domain.composition;

import com.toefly.planning.domain.composition.GreedyWeekPacker.Packed;
import com.toefly.planning.domain.week.TaskOrigin;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.WeekSection;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The deterministic pool of WeekGenerator (docs/06-ai-agent.md §5.2, docs/12 phase 5c decision 1): the week of the
 * rules (GreedyWeekPacker, with the focus types as weak types and the grammar practice of AC-14.05.2) plus
 * alternative item sets from the local catalog, so every item-set slot of the standard composition has 3 to 5
 * candidates; the focus comes first. The model can only choose among these refs.
 */
public final class CandidateCollector {

    /** Candidates of a task type: the sets of the rules and up to two alternatives, at least 3 in all. */
    static final int MIN_PER_TYPE = 3;

    static final int MAX_ALTERNATIVES = 2;
    static final int MAX_RESOURCES = 4;

    private CandidateCollector() {}

    private record Draft(WeekTask task, boolean mandatory, boolean baseline, List<String> skills, int index) {
        boolean focus() {
            return !skills.isEmpty();
        }
    }

    public static CandidatePool collect(CompositionRequest request, CompositionRules rules, WeekFocus focus) {
        Packed packed = GreedyWeekPacker.pack(request, rules, focus);
        List<Draft> drafts = new ArrayList<>();
        Map<TaskType, Integer> perType = new LinkedHashMap<>();
        Set<String> resources = new HashSet<>();
        for (WeekTask task : packed.tasks()) {
            String topic = packed.grammarPractice().get(task.id());
            boolean practice = topic != null;
            List<String> skills =
                    practice ? List.of(topic) : task.taskType() == null ? List.of() : focus.skillsOf(task.taskType());
            drafts.add(new Draft(task, mandatory(task, practice), true, skills, drafts.size()));
            if (task.kind() == WeekTaskKind.ITEM_SET && task.taskType() != null && !practice) {
                perType.merge(task.taskType(), 1, Integer::sum);
            }
            if (task.kind() == WeekTaskKind.EXTERNAL_RESOURCE && task.refId() != null) {
                resources.add(task.refId());
            }
        }
        perType.forEach((type, count) -> {
            int wanted = Math.min(MAX_ALTERNATIVES, Math.max(1, MIN_PER_TYPE - count + 1));
            for (int i = 0; i < wanted; i++) {
                Optional<ItemSetCandidate> set = request.supply().next(type);
                if (set.isEmpty()) {
                    break;
                }
                if (set.get().repeat()) {
                    request.supply().giveBack(set.get()); // an alternative is always a fresh set (INV-WK-04)
                    break;
                }
                WeekTask task = alternative(set.get(), rules, focus, count);
                drafts.add(new Draft(task, false, false, focus.skillsOf(type), drafts.size()));
            }
        });
        int resourceCount = resources.size();
        for (ResourceLink link : request.resources()) {
            if (resourceCount >= MAX_RESOURCES || resources.contains(link.url())) {
                continue;
            }
            drafts.add(new Draft(resource(link, rules), false, false, List.of(), drafts.size()));
            resourceCount++;
        }
        drafts.sort(Comparator.comparingInt((Draft draft) -> draft.focus() ? 0 : 1)
                .thenComparingInt(draft -> draft.task().section().ordinal())
                .thenComparingInt(draft -> draft.baseline() ? 0 : 1)
                .thenComparingInt(Draft::index));
        List<WeekCandidate> candidates = new ArrayList<>();
        for (Draft draft : drafts) {
            candidates.add(new WeekCandidate(
                    ref(candidates.size() + 1), draft.task(), draft.mandatory(), draft.baseline(), draft.skills()));
        }
        return new CandidatePool(candidates, packed.tasks());
    }

    /** {@code c01}…{@code c99}, then {@code c100}: the pattern of WeekDraftOutput. */
    static String ref(int number) {
        return String.format(Locale.ROOT, "c%02d", number);
    }

    private static boolean mandatory(WeekTask task, boolean practice) {
        return practice
                || task.origin() == TaskOrigin.TUTOR
                || task.kind() == WeekTaskKind.GRAMMAR_UNIT
                || task.kind() == WeekTaskKind.VOCABULARY_UNIT
                || task.kind() == WeekTaskKind.VOCABULARY_REVIEW
                || task.kind() == WeekTaskKind.MOCK_EXAM;
    }

    private static WeekTask alternative(ItemSetCandidate set, CompositionRules rules, WeekFocus focus, int count) {
        String why = focus.pointFor(set.taskType())
                .map(GreedyWeekPacker::why)
                .orElse("Standard week: " + count + " × " + set.taskType().label());
        return WeekTask.pending(
                WeekTaskKind.ITEM_SET,
                set.taskType().section(),
                set.taskType(),
                set.itemSetId().toString(),
                set.title(),
                set.estimatedMinutes() > 0 ? set.estimatedMinutes() : rules.minutesOf(set.taskType()),
                TaskOrigin.SYSTEM,
                why,
                false);
    }

    private static WeekTask resource(ResourceLink link, CompositionRules rules) {
        return WeekTask.pending(
                WeekTaskKind.EXTERNAL_RESOURCE,
                WeekSection.RESOURCES,
                null,
                link.url(),
                link.title(),
                rules.resourceMinutes(),
                TaskOrigin.SYSTEM,
                "Extra reading on toeflresources.com",
                false);
    }
}
