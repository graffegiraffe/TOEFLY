package com.toefly.analytics.domain.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.analytics.domain.common.Section;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeeklyProgressTest {

    static final UUID USER = UUID.randomUUID();
    static final LocalDate DAY1 = LocalDate.of(2026, 11, 2);
    static final ZoneId MINSK = ZoneId.of("Europe/Minsk");

    @Test
    @DisplayName("AC-13.02.1: each week carries its progress and the last estimate published by its last day")
    void lastEstimateOfWeek() {
        WeekSummary week1 = WeekSummary.published(UUID.randomUUID(), USER, 1, DAY1, 18, at(DAY1))
                .taskDone(40, at(DAY1.plusDays(1)))
                .completed(DAY1, DAY1.plusDays(6), 72.2, 18, 13, "PROGRESS_70", at(DAY1.plusDays(6)));
        WeekSummary week2 = WeekSummary.published(UUID.randomUUID(), USER, 2, DAY1.plusDays(7), 20, at(DAY1));
        List<BandPoint> points = List.of(
                point(at(DAY1.plusDays(2)), 3.5), point(at(DAY1.plusDays(5)), 4.0), point(at(DAY1.plusDays(9)), 4.5));

        List<WeeklyProgress> chart = WeeklyProgress.of(List.of(week2, week1), points, MINSK);

        assertThat(chart).extracting(WeeklyProgress::weekNo).containsExactly(1, 2);
        assertThat(chart.getFirst().overallPoint()).isEqualTo(4.0);
        assertThat(chart.getFirst().completed()).isTrue();
        assertThat(chart.getFirst().tasksDone()).isEqualTo(13);
        assertThat(chart.get(1).overallPoint()).isEqualTo(4.5);
        assertThat(chart.get(1).sections()).containsEntry(Section.READING, 4.5);
    }

    @Test
    @DisplayName("AC-13.02.2: a week without an estimate has no point, so the line breaks")
    void gap() {
        WeekSummary week = WeekSummary.published(UUID.randomUUID(), USER, 3, DAY1, 18, at(DAY1));
        WeekSummary unknown = new WeekSummary(UUID.randomUUID(), USER, 4, null, null, 0, 0, 1, false, null, at(DAY1));

        List<WeeklyProgress> chart =
                WeeklyProgress.of(List.of(week, unknown), List.of(point(at(DAY1.plusDays(20)), 4.0)), MINSK);

        assertThat(chart).allSatisfy(w -> assertThat(w.overallPoint()).isNull());
        assertThat(unknown.lastDay()).isNull();
        assertThat(week.lastDay()).isEqualTo(DAY1.plusDays(6));
    }

    @Test
    @DisplayName("INV-ANL-02: a completed week ignores later task events; an older progress keeps the newer value")
    void summaryMonotonic() {
        WeekSummary week = WeekSummary.published(UUID.randomUUID(), USER, 1, DAY1, 10, at(DAY1))
                .taskDone(20, at(DAY1.plusDays(2)))
                .taskDone(10, at(DAY1.plusDays(1)));
        assertThat(week.progressPercent()).isEqualTo(20);
        assertThat(week.tasksDone()).isEqualTo(2);

        WeekSummary closed = week.completed(DAY1, DAY1.plusDays(9), 60, 10, 6, "DAY_10", at(DAY1.plusDays(9)));
        assertThat(closed.taskDone(99, at(DAY1.plusDays(10)))).isEqualTo(closed);
        assertThat(closed.published(DAY1, 11).tasksTotal()).isEqualTo(11);
    }

    private static BandPoint point(Instant at, double overall) {
        return new BandPoint(
                UUID.randomUUID(), at, overall, overall - 0.5, overall + 0.5, Map.of(Section.READING, overall));
    }

    private static Instant at(LocalDate day) {
        return day.atTime(12, 0).atZone(MINSK).toInstant();
    }
}
