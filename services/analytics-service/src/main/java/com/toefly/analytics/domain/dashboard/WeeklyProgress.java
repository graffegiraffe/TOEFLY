package com.toefly.analytics.domain.dashboard;

import com.toefly.analytics.domain.common.Section;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A point of the weeks chart: the progress of the week and the last band estimate published by its last day. A week
 * without an estimate has no band point, so the line breaks there (AC-13.02.2).
 */
public record WeeklyProgress(
        int weekNo,
        @Nullable LocalDate day1,
        double progressPercent,
        int tasksDone,
        int tasksTotal,
        boolean completed,
        @Nullable Double overallPoint,
        @Nullable Double overallLow,
        @Nullable Double overallHigh,
        Map<Section, Double> sections) {

    public WeeklyProgress {
        sections = Map.copyOf(sections);
    }

    /** Joins the weeks with the band points; points are matched in the learner's time zone. */
    public static List<WeeklyProgress> of(List<WeekSummary> weeks, List<BandPoint> points, ZoneId zone) {
        List<BandPoint> ordered = new ArrayList<>(points);
        ordered.sort(Comparator.comparing(BandPoint::at));
        return weeks.stream()
                .sorted(Comparator.comparingInt(WeekSummary::weekNo))
                .map(week -> {
                    BandPoint point = lastBy(ordered, week, zone);
                    return new WeeklyProgress(
                            week.weekNo(),
                            week.day1(),
                            week.progressPercent(),
                            week.tasksDone(),
                            week.tasksTotal(),
                            week.completed(),
                            point == null ? null : point.overallPoint(),
                            point == null ? null : point.overallLow(),
                            point == null ? null : point.overallHigh(),
                            point == null ? Map.of() : point.sections());
                })
                .toList();
    }

    private static @Nullable BandPoint lastBy(List<BandPoint> ordered, WeekSummary week, ZoneId zone) {
        LocalDate first = week.day1();
        LocalDate last = week.lastDay();
        if (first == null || last == null) {
            return null;
        }
        BandPoint found = null;
        for (BandPoint point : ordered) {
            LocalDate day = LocalDate.ofInstant(point.at(), zone);
            if (!day.isBefore(first) && !day.isAfter(last)) {
                found = point;
            }
        }
        return found;
    }
}
