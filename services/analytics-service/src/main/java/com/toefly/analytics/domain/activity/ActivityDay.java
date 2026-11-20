package com.toefly.analytics.domain.activity;

import com.toefly.analytics.domain.common.TaskType;
import com.toefly.analytics.domain.common.WeekSection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code ActivityDay} (docs/03 §6.15): active time of one calendar day of the learner by week section and task type,
 * done tasks, the pace against ETS norms and the daily goal. Sums are commutative, so the order of events does not
 * matter. The goal of a day is fixed when the day starts and, once met, stays met (INV-ANL-03, AC-19.01.4).
 */
public record ActivityDay(
        UUID userId,
        LocalDate date,
        Map<WeekSection, Integer> secondsBySection,
        Map<TaskType, Integer> secondsByTaskType,
        Map<TaskType, PaceTally> pace,
        int tasksDone,
        int goalMinutes,
        boolean dayPlanDone,
        boolean goalMet,
        @Nullable Instant lastActiveAt) {

    public ActivityDay {
        secondsBySection = copy(secondsBySection, WeekSection.class);
        secondsByTaskType = copy(secondsByTaskType, TaskType.class);
        pace = copy(pace, TaskType.class);
    }

    public static ActivityDay start(UUID userId, LocalDate date, int goalMinutes) {
        return new ActivityDay(userId, date, Map.of(), Map.of(), Map.of(), 0, goalMinutes, false, false, null);
    }

    public int activeSeconds() {
        return secondsBySection.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Adds the active time of a closed attempt (AC-16.01.5). A suspicious entry counts as time but not for the pace;
     * {@code normSeconds} is {@code null} when the norm of the attempt is unknown.
     */
    public ActivityDay timeTracked(
            TaskType type,
            @Nullable WeekSection section,
            int seconds,
            @Nullable Integer normSeconds,
            boolean suspicious,
            Instant at) {
        Map<WeekSection, Integer> bySection = new EnumMap<>(WeekSection.class);
        bySection.putAll(secondsBySection);
        bySection.merge(section == null ? type.weekSection() : section, seconds, Integer::sum);
        Map<TaskType, Integer> byType = new EnumMap<>(TaskType.class);
        byType.putAll(secondsByTaskType);
        byType.merge(type, seconds, Integer::sum);
        Map<TaskType, PaceTally> nextPace = new EnumMap<>(TaskType.class);
        nextPace.putAll(pace);
        if (!suspicious && normSeconds != null && normSeconds > 0) {
            nextPace.merge(type, PaceTally.NONE.plus(seconds, normSeconds), PaceTally::plus);
        }
        return new ActivityDay(
                        userId,
                        date,
                        bySection,
                        byType,
                        nextPace,
                        tasksDone,
                        goalMinutes,
                        dayPlanDone,
                        goalMet,
                        latest(at))
                .evaluated();
    }

    public ActivityDay taskDone(Instant at) {
        return new ActivityDay(
                        userId,
                        date,
                        secondsBySection,
                        secondsByTaskType,
                        pace,
                        tasksDone + 1,
                        goalMinutes,
                        dayPlanDone,
                        goalMet,
                        latest(at))
                .evaluated();
    }

    /** Every task planned for this day is done (overdue tasks of earlier days do not count, AC-19.02.1). */
    public ActivityDay withDayPlanDone() {
        return new ActivityDay(
                        userId,
                        date,
                        secondsBySection,
                        secondsByTaskType,
                        pace,
                        tasksDone,
                        goalMinutes,
                        true,
                        goalMet,
                        lastActiveAt)
                .evaluated();
    }

    /** Share of the goal ring: {@code min(1, active time / goal)}, full once the plan of the day is done (AC-19.02.2). */
    public double goalProgress() {
        if (goalMet || dayPlanDone) {
            return 1;
        }
        if (goalMinutes <= 0) {
            return 0;
        }
        return Math.min(1, activeSeconds() / (goalMinutes * 60.0));
    }

    private ActivityDay evaluated() {
        boolean met = goalMet || dayPlanDone || (goalMinutes > 0 && activeSeconds() >= goalMinutes * 60);
        if (met == goalMet) {
            return this;
        }
        return new ActivityDay(
                userId,
                date,
                secondsBySection,
                secondsByTaskType,
                pace,
                tasksDone,
                goalMinutes,
                dayPlanDone,
                true,
                lastActiveAt);
    }

    private Instant latest(Instant at) {
        return lastActiveAt == null || at.isAfter(lastActiveAt) ? at : lastActiveAt;
    }

    private static <K extends Enum<K>, V> Map<K, V> copy(Map<K, V> source, Class<K> type) {
        if (source.isEmpty()) {
            return Map.of();
        }
        Map<K, V> copy = new EnumMap<>(type);
        copy.putAll(source);
        return java.util.Collections.unmodifiableMap(copy);
    }
}
