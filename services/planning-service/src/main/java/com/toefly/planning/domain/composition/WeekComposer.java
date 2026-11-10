package com.toefly.planning.domain.composition;

import com.toefly.planning.domain.week.TaskOrigin;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.WeekSection;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Composition of a week (docs/03-domain-model.md §10.2): base counts scaled by the daily minutes, more sets of
 * weak types, the book tracks, resources and the mock exam, then compression to the budget and the fill step.
 */
public final class WeekComposer {

    static final List<WeekSection> EXAM_SECTIONS =
            List.of(WeekSection.READING, WeekSection.LISTENING, WeekSection.WRITING, WeekSection.SPEAKING);

    /** At most two tasks of a type a day (AC-04.03.1), so at most 14 in a week. */
    static final int MAX_PER_TYPE = 14;

    private enum Slot {
        EXAM_SET,
        EXTRA_SET,
        GRAMMAR_EXERCISE,
        ROUND_UP,
        COLLINS_GRAMMAR,
        COLLINS_VOCABULARY,
        REVIEW,
        RESOURCE,
        MOCK
    }

    private record Entry(Slot slot, WeekTask task, @Nullable ItemSetCandidate set) {}

    private final CompositionRequest request;
    private final CompositionRules rules;
    private final List<Entry> entries = new ArrayList<>();
    private final Set<TaskType> weak;

    private WeekComposer(CompositionRequest request, CompositionRules rules) {
        this.request = request;
        this.rules = rules;
        this.weak = Set.copyOf(request.weakTypes());
    }

    /** Pending tasks of the week without days; {@code Week.publish} spreads them over the days. */
    public static List<WeekTask> compose(CompositionRequest request, CompositionRules rules) {
        WeekComposer composer = new WeekComposer(request, rules);
        composer.build();
        composer.compress();
        composer.makeRoomForMockExam();
        composer.fill();
        return composer.entries.stream().map(Entry::task).toList();
    }

    // ---- budget ----

    private int minutesPerDay() {
        return request.minutesPerDay();
    }

    private boolean minimal() {
        return minutesPerDay() <= rules.minimalModeMinutes();
    }

    double budgetMax() {
        return minutesPerDay() * 7.0 * rules.budgetMaxFactor();
    }

    double budgetMin() {
        return minutesPerDay() * 7.0 * rules.budgetMinFactor();
    }

    private double total() {
        return entries.stream()
                .mapToDouble(entry -> entry.task().estimatedMinutes())
                .sum();
    }

    // ---- first draft ----

    private void build() {
        Map<TaskType, Integer> counts = counts();
        for (Map.Entry<TaskType, Integer> count : counts.entrySet()) {
            TaskType type = count.getKey();
            int base = minimal() ? 1 : baseCount(type);
            for (int i = 0; i < count.getValue(); i++) {
                Optional<ItemSetCandidate> set = request.supply().next(type);
                if (set.isEmpty()) {
                    break;
                }
                boolean extra = i >= base;
                String why = extra ? weakReason(type) : "Standard week: " + base + " × " + type.label();
                addSet(Slot.EXAM_SET, set.get(), extra ? TaskOrigin.AI : TaskOrigin.SYSTEM, why);
            }
        }
        boolean odd = request.weekNo() % 2 == 1;
        request.roundUpUnit()
                .filter(unit -> !minimal() || odd)
                .ifPresent(unit -> addUnit(
                        Slot.ROUND_UP,
                        WeekTaskKind.GRAMMAR_UNIT,
                        WeekSection.GRAMMAR,
                        unit,
                        "Next unit of New Round-Up 5"));
        if (!minimal()) {
            request.collinsGrammarUnit()
                    .ifPresent(unit -> addUnit(
                            Slot.COLLINS_GRAMMAR,
                            WeekTaskKind.GRAMMAR_UNIT,
                            WeekSection.GRAMMAR,
                            unit,
                            "Next unit of Collins Grammar for the TOEFL"));
            request.collinsVocabularyUnit()
                    .ifPresent(unit -> addUnit(
                            Slot.COLLINS_VOCABULARY,
                            WeekTaskKind.VOCABULARY_UNIT,
                            WeekSection.VOCABULARY,
                            unit,
                            "Next unit of Collins Vocabulary for the TOEFL"));
            int exercises = (int) Math.floor(scale()) - 1;
            if (request.availableTypes().contains(TaskType.GRAMMAR_EXERCISE)) {
                for (int i = 0; i < exercises; i++) {
                    Optional<ItemSetCandidate> set = request.supply().next(TaskType.GRAMMAR_EXERCISE);
                    if (set.isEmpty()) {
                        break;
                    }
                    addSet(Slot.GRAMMAR_EXERCISE, set.get(), TaskOrigin.AI, "Extra grammar practice for a long day");
                }
            }
        }
        if (request.vocabularyReview()) {
            for (int day = 0; day < 7; day++) {
                entries.add(new Entry(
                        Slot.REVIEW,
                        WeekTask.pending(
                                WeekTaskKind.VOCABULARY_REVIEW,
                                WeekSection.VOCABULARY,
                                null,
                                null,
                                "Vocabulary review",
                                rules.vocabularyReviewMinutes(),
                                TaskOrigin.SYSTEM,
                                "Daily spaced repetition of your words",
                                false),
                        null));
            }
        }
        addResources();
        if (request.mockExam()) {
            entries.add(new Entry(
                    Slot.MOCK,
                    WeekTask.pending(
                            WeekTaskKind.MOCK_EXAM,
                            WeekSection.READING,
                            null,
                            null,
                            "Mock exam",
                            rules.mockExamMinutes(),
                            TaskOrigin.SYSTEM,
                            "Mock exam of week " + request.weekNo(),
                            false),
                    null));
        }
    }

    private double scale() {
        return minutesPerDay() / (double) rules.baseMinutesPerDay();
    }

    private int baseCount(TaskType type) {
        return Math.max(1, (int) Math.round(rules.baseCount(type) * scale()));
    }

    /** Tasks of each available exam type, in the order of the week table. */
    private Map<TaskType, Integer> counts() {
        Map<TaskType, Integer> counts = new LinkedHashMap<>();
        List<TaskType> available = TaskType.EXAM.stream()
                .filter(request.availableTypes()::contains)
                .toList();
        if (minimal()) {
            for (WeekSection section : EXAM_SECTIONS) {
                List<TaskType> ofSection = available.stream()
                        .filter(type -> type.section() == section)
                        .toList();
                if (!ofSection.isEmpty()) {
                    counts.put(ofSection.get((request.weekNo() - 1) % ofSection.size()), 1);
                }
            }
        } else {
            for (TaskType type : available) {
                counts.put(type, baseCount(type));
            }
        }
        for (TaskType type : request.weakTypes()) {
            counts.computeIfPresent(type, (key, count) -> (int) Math.ceil(count * rules.weakFactor()));
        }
        counts.replaceAll((type, count) -> Math.min(MAX_PER_TYPE, count));
        return counts;
    }

    private void addResources() {
        int wanted = minimal() ? 1 : 2;
        Set<WeekSection> weakSections = new HashSet<>();
        for (TaskType type : request.weakTypes()) {
            weakSections.add(type.section());
        }
        List<ResourceLink> links = new ArrayList<>(request.resources());
        links.sort(Comparator.comparingInt(link -> link.sections().stream().anyMatch(weakSections::contains) ? 0 : 1));
        for (ResourceLink link : links.subList(0, Math.min(wanted, links.size()))) {
            entries.add(new Entry(
                    Slot.RESOURCE,
                    WeekTask.pending(
                            WeekTaskKind.EXTERNAL_RESOURCE,
                            WeekSection.RESOURCES,
                            null,
                            link.url(),
                            link.title(),
                            rules.resourceMinutes(),
                            TaskOrigin.SYSTEM,
                            "Extra reading on toeflresources.com",
                            false),
                    null));
        }
    }

    private void addSet(Slot slot, ItemSetCandidate set, TaskOrigin origin, String why) {
        double minutes = set.estimatedMinutes() > 0 ? set.estimatedMinutes() : rules.minutesOf(set.taskType());
        String rationale = set.repeat() ? "Repeat: no new set of this type is left. " + why : why;
        entries.add(new Entry(
                slot,
                WeekTask.pending(
                        WeekTaskKind.ITEM_SET,
                        set.taskType().section(),
                        set.taskType(),
                        set.itemSetId().toString(),
                        set.title(),
                        minutes,
                        origin,
                        rationale,
                        set.repeat()),
                set));
    }

    private void addUnit(Slot slot, WeekTaskKind kind, WeekSection section, UnitRef unit, String why) {
        entries.add(new Entry(
                slot,
                WeekTask.pending(
                        kind,
                        section,
                        null,
                        unit.unitId().toString(),
                        unit.title(),
                        rules.unitMinutes(),
                        TaskOrigin.SYSTEM,
                        why,
                        false),
                null));
    }

    private static String weakReason(TaskType type) {
        return "Added: more " + type.lowerLabel() + " — a weak type in the last two weeks";
    }

    // ---- 1. compression to the budget ----

    private void compress() {
        while (total() > budgetMax() + 1e-9) {
            Optional<Entry> reducible = nextReducible();
            if (reducible.isEmpty()) {
                return; // the smallest possible week may exceed the budget
            }
            remove(reducible.get());
        }
    }

    private Optional<Entry> nextReducible() {
        List<Entry> resources = ofSlot(Slot.RESOURCE);
        if (resources.size() > 1) {
            return Optional.of(resources.getLast());
        }
        Optional<Entry> collinsGrammar = ofSlot(Slot.COLLINS_GRAMMAR).stream().findFirst();
        if (collinsGrammar.isPresent()) {
            return collinsGrammar;
        }
        Optional<Entry> strong = longestOfTypesWithSpare(type -> !weak.contains(type));
        if (strong.isPresent()) {
            return strong;
        }
        Optional<Entry> collinsVocabulary =
                ofSlot(Slot.COLLINS_VOCABULARY).stream().findFirst();
        if (collinsVocabulary.isPresent()) {
            return collinsVocabulary;
        }
        List<TaskType> leastWeakFirst = new ArrayList<>(request.weakTypes()).reversed();
        for (TaskType type : leastWeakFirst) {
            Optional<Entry> entry = longestOfTypesWithSpare(candidate -> candidate == type);
            if (entry.isPresent()) {
                return entry;
            }
        }
        return Optional.empty();
    }

    /** The longest item set of a type that has more than one set in the week. */
    private Optional<Entry> longestOfTypesWithSpare(java.util.function.Predicate<TaskType> types) {
        Map<TaskType, Integer> counts = typeCounts();
        return entries.stream()
                .filter(entry -> entry.set() != null)
                .filter(entry -> types.test(entry.set().taskType()))
                .filter(entry -> counts.getOrDefault(entry.set().taskType(), 0) > 1)
                .max(Comparator.comparingDouble((Entry entry) -> entry.task().estimatedMinutes())
                        .thenComparingInt(entries::indexOf));
    }

    // ---- 2. room for the mock exam ----

    private void makeRoomForMockExam() {
        if (ofSlot(Slot.MOCK).isEmpty()) {
            return;
        }
        double removed = 0;
        while (removed < rules.mockExamMinutes() - 1e-9) {
            Optional<Entry> victim = mockVictim(type -> !weak.contains(type));
            if (victim.isEmpty()) {
                victim = mockVictim(weak::contains);
            }
            if (victim.isEmpty()) {
                return;
            }
            removed += victim.get().task().estimatedMinutes();
            remove(victim.get());
        }
    }

    /** A whole set from the exam section with the most minutes, strong types first, never the last set of a type. */
    private Optional<Entry> mockVictim(java.util.function.Predicate<TaskType> types) {
        Map<WeekSection, Double> bySection = new EnumMap<>(WeekSection.class);
        for (Entry entry : entries) {
            if (entry.set() != null && entry.task().section().exam()) {
                bySection.merge(entry.task().section(), entry.task().estimatedMinutes(), Double::sum);
            }
        }
        List<WeekSection> sections = bySection.entrySet().stream()
                .sorted(Map.Entry.<WeekSection, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
        for (WeekSection section : sections) {
            Optional<Entry> entry = longestOfTypesWithSpare(type -> type.section() == section && types.test(type));
            if (entry.isPresent()) {
                return entry;
            }
        }
        return Optional.empty();
    }

    // ---- 3. fill to the lower bound ----

    private void fill() {
        Set<TaskType> exhausted = new HashSet<>();
        while (total() < budgetMin() - 1e-9) {
            boolean added = false;
            for (TaskType type : fillOrder()) {
                if (total() >= budgetMin() - 1e-9) {
                    return;
                }
                if (exhausted.contains(type) || typeCounts().getOrDefault(type, 0) >= MAX_PER_TYPE) {
                    continue;
                }
                Optional<ItemSetCandidate> set = request.supply().next(type);
                if (set.isEmpty()) {
                    exhausted.add(type);
                    continue;
                }
                double minutes =
                        set.get().estimatedMinutes() > 0 ? set.get().estimatedMinutes() : rules.minutesOf(type);
                if (total() + minutes > budgetMax() + 1e-9) {
                    request.supply().giveBack(set.get());
                    exhausted.add(type);
                    continue;
                }
                String why = weak.contains(type) ? weakReason(type) : "Fills your daily time with " + type.lowerLabel();
                addSet(Slot.EXTRA_SET, set.get(), TaskOrigin.AI, why);
                added = true;
            }
            if (!added) {
                return;
            }
        }
    }

    /** Weak types from the weakest, then the types with the fewest tasks in the last four weeks. */
    private List<TaskType> fillOrder() {
        List<TaskType> order = new ArrayList<>();
        for (TaskType type : request.weakTypes()) {
            if (request.availableTypes().contains(type) && type.section().exam()) {
                order.add(type);
            }
        }
        Map<TaskType, Integer> counts = typeCounts();
        TaskType.EXAM.stream()
                .filter(request.availableTypes()::contains)
                .filter(type -> !weak.contains(type))
                .sorted(Comparator.comparingInt((TaskType type) ->
                                request.recentTypeCounts().getOrDefault(type, 0) + counts.getOrDefault(type, 0))
                        .thenComparingInt(TaskType.EXAM::indexOf))
                .forEach(order::add);
        return order;
    }

    // ---- helpers ----

    private List<Entry> ofSlot(Slot slot) {
        return entries.stream().filter(entry -> entry.slot() == slot).toList();
    }

    private Map<TaskType, Integer> typeCounts() {
        Map<TaskType, Integer> counts = new EnumMap<>(TaskType.class);
        for (Entry entry : entries) {
            if (entry.set() != null) {
                counts.merge(entry.set().taskType(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private void remove(Entry entry) {
        entries.remove(entry);
        if (entry.set() != null) {
            request.supply().giveBack(entry.set());
        }
    }
}
