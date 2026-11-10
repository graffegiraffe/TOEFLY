package com.toefly.planning.domain.composition;

import com.toefly.planning.domain.week.TaskType;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** {@code perType} sets of every type with the minutes of the rules; repeats when {@code repeats} is set. */
final class FakeSupply implements SetSupply {

    private final Map<TaskType, Deque<ItemSetCandidate>> sets = new EnumMap<>(TaskType.class);

    FakeSupply(int perType, boolean repeats) {
        CompositionRules rules = CompositionRules.standard();
        for (TaskType type : TaskType.values()) {
            Deque<ItemSetCandidate> queue = new ArrayDeque<>();
            for (int i = 0; i < perType; i++) {
                queue.add(new ItemSetCandidate(
                        UUID.randomUUID(), type, type.label() + ": set " + i, rules.minutesOf(type), 0, repeats));
            }
            sets.put(type, queue);
        }
    }

    @Override
    public Optional<ItemSetCandidate> next(TaskType type) {
        return Optional.ofNullable(sets.get(type).pollFirst());
    }

    @Override
    public void giveBack(ItemSetCandidate candidate) {
        sets.get(candidate.taskType()).addFirst(candidate);
    }
}
