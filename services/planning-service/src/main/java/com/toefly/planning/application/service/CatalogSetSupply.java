package com.toefly.planning.application.service;

import com.toefly.planning.application.port.out.ItemSetCatalogPort;
import com.toefly.planning.application.port.out.ItemSetCatalogPort.CatalogEntry;
import com.toefly.planning.domain.composition.ItemSetCandidate;
import com.toefly.planning.domain.composition.SetSupply;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.WeekSection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Item sets for one week (docs/03-domain-model.md §7.2): fresh sets nearest to {@code b_target = θ − 0.847}
 * (−0.2 for weak types, +0.2 for strong ones), a random pick among the five nearest; when no fresh set is left, a
 * set the learner did before as a repeat (AC-04.01.4).
 */
final class CatalogSetSupply implements SetSupply {

    static final double LOG_ODDS_TARGET = 0.847;
    static final double WEAK_SHIFT = -0.2;
    static final double STRONG_SHIFT = 0.2;
    static final int NEAREST = 5;

    private final ItemSetCatalogPort catalog;
    private final String examFormat;
    private final Set<String> assigned;
    private final Map<TaskType, List<UUID>> done;
    private final Map<WeekSection, Double> theta;
    private final Set<TaskType> weak;
    private final Random random;
    private final Map<TaskType, Deque<ItemSetCandidate>> queues = new EnumMap<>(TaskType.class);
    private final Map<UUID, CatalogEntry> known = new java.util.HashMap<>();
    private final Set<UUID> taken = new HashSet<>();

    CatalogSetSupply(
            ItemSetCatalogPort catalog,
            String examFormat,
            Set<String> assigned,
            Map<TaskType, List<UUID>> done,
            Map<WeekSection, Double> theta,
            Set<TaskType> weak,
            long seed) {
        this.catalog = catalog;
        this.examFormat = examFormat;
        this.assigned = assigned;
        this.done = done;
        this.theta = theta;
        this.weak = weak;
        this.random = new Random(seed);
    }

    @Override
    public Optional<ItemSetCandidate> next(TaskType type) {
        Deque<ItemSetCandidate> queue = queues.computeIfAbsent(type, this::load);
        while (!queue.isEmpty()) {
            ItemSetCandidate candidate = queue.pollFirst();
            if (taken.add(candidate.itemSetId())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    @Override
    public void giveBack(ItemSetCandidate candidate) {
        if (taken.remove(candidate.itemSetId())) {
            queues.computeIfAbsent(candidate.taskType(), this::load).addFirst(candidate);
        }
    }

    private Deque<ItemSetCandidate> load(TaskType type) {
        List<CatalogEntry> visible = catalog.visible(type).stream()
                .filter(entry -> entry.examFormat().equals(examFormat) && entry.ownerTutorId() == null)
                .toList();
        visible.forEach(entry -> known.put(entry.itemSetId(), entry));
        double target = theta.getOrDefault(type.section(), 0.0)
                - LOG_ODDS_TARGET
                + (weak.contains(type) ? WEAK_SHIFT : STRONG_SHIFT);
        List<CatalogEntry> fresh = new ArrayList<>(visible.stream()
                .filter(entry -> !assigned.contains(entry.itemSetId().toString()))
                .sorted(Comparator.comparingDouble((CatalogEntry entry) -> Math.abs(entry.difficultyB() - target))
                        .thenComparing(CatalogEntry::itemSetId))
                .toList());
        Deque<ItemSetCandidate> queue = new ArrayDeque<>();
        while (!fresh.isEmpty()) {
            int pick = random.nextInt(Math.min(NEAREST, fresh.size()));
            queue.addLast(candidate(fresh.remove(pick), false));
        }
        for (UUID id : done.getOrDefault(type, List.of())) {
            CatalogEntry entry = known.get(id);
            if (entry != null) {
                queue.addLast(candidate(entry, true));
            }
        }
        return queue;
    }

    private static ItemSetCandidate candidate(CatalogEntry entry, boolean repeat) {
        return new ItemSetCandidate(
                entry.itemSetId(),
                entry.taskType(),
                title(entry),
                entry.estimatedMinutes(),
                entry.difficultyB(),
                repeat);
    }

    /** {@code "Academic passage: Desert plants"}; the event of content-service carries only the topic. */
    static String title(CatalogEntry entry) {
        String topic = entry.topic().isBlank() ? "practice set" : entry.topic().strip();
        String title = entry.taskType().label() + ": " + Character.toUpperCase(topic.charAt(0)) + topic.substring(1);
        return title.length() <= 200 ? title : title.substring(0, 199) + "…";
    }
}
