package com.toefly.analytics.domain.activity;

import com.toefly.analytics.domain.common.TaskType;
import com.toefly.analytics.domain.common.WeekSection;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Time of a period against the exam pace (US-16.02) and against the plan (AC-16.03.1): totals by week section, by task
 * type with the pace ratio, and by day for the bar chart.
 */
public record TimeReport(
        LocalDate from,
        LocalDate to,
        int totalSeconds,
        int plannedSeconds,
        Map<WeekSection, Integer> bySection,
        List<TaskTypeTime> byTaskType,
        List<DayTime> byDay) {

    public TimeReport {
        bySection = Map.copyOf(bySection);
        byTaskType = List.copyOf(byTaskType);
        byDay = List.copyOf(byDay);
    }

    public record TaskTypeTime(TaskType taskType, int activeSeconds, PaceTally pace) {}

    public record DayTime(LocalDate date, int activeSeconds, Map<WeekSection, Integer> bySection, boolean goalMet) {

        public DayTime {
            bySection = Map.copyOf(bySection);
        }
    }

    /** Builds the report of {@code [from, to)} with the plan counted over all of it. */
    public static TimeReport of(
            List<ActivityDay> days, LocalDate from, LocalDate to, LocalDate today, int minutesPerDay) {
        return of(days, from, to, from, today, minutesPerDay);
    }

    /**
     * Builds the report of {@code [from, to)}; the plan counts the days from {@code planFrom} (no plan before the study
     * plan started) to {@code today}, so future days do not count as behind.
     */
    public static TimeReport of(
            List<ActivityDay> days,
            LocalDate from,
            LocalDate to,
            LocalDate planFrom,
            LocalDate today,
            int minutesPerDay) {
        Map<WeekSection, Integer> bySection = new EnumMap<>(WeekSection.class);
        Map<TaskType, Integer> seconds = new EnumMap<>(TaskType.class);
        Map<TaskType, PaceTally> pace = new EnumMap<>(TaskType.class);
        int total = 0;
        List<ActivityDay> inRange = days.stream()
                .filter(day -> !day.date().isBefore(from) && day.date().isBefore(to))
                .sorted(Comparator.comparing(ActivityDay::date))
                .toList();
        for (ActivityDay day : inRange) {
            day.secondsBySection().forEach((section, value) -> bySection.merge(section, value, Integer::sum));
            day.secondsByTaskType().forEach((type, value) -> seconds.merge(type, value, Integer::sum));
            day.pace().forEach((type, value) -> pace.merge(type, value, PaceTally::plus));
            total += day.activeSeconds();
        }
        List<TaskTypeTime> byType = seconds.entrySet().stream()
                .map(e -> new TaskTypeTime(e.getKey(), e.getValue(), pace.getOrDefault(e.getKey(), PaceTally.NONE)))
                .toList();
        List<DayTime> byDay = inRange.stream()
                .map(day -> new DayTime(day.date(), day.activeSeconds(), day.secondsBySection(), day.goalMet()))
                .toList();
        LocalDate planEnd = to.isAfter(today.plusDays(1)) ? today.plusDays(1) : to;
        long plannedDays = Math.max(0, ChronoUnit.DAYS.between(planFrom.isAfter(from) ? planFrom : from, planEnd));
        return new TimeReport(from, to, total, (int) (plannedDays * minutesPerDay * 60), bySection, byType, byDay);
    }
}
