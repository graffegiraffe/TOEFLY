package com.toefly.analytics.domain.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.analytics.domain.common.TaskType;
import com.toefly.analytics.domain.common.WeekSection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimeReportTest {

    static final UUID USER = UUID.randomUUID();
    static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    static final Instant AT = Instant.parse("2026-11-20T10:00:00Z");

    @Test
    @DisplayName("AC-16.02.1: time by section, by task type and by day with totals of the period")
    void aggregates() {
        List<ActivityDay> days = List.of(
                ActivityDay.start(USER, TODAY.minusDays(2), 30)
                        .timeTracked(TaskType.READ_DAILY_LIFE, null, 600, 480, false, AT),
                ActivityDay.start(USER, TODAY, 30)
                        .timeTracked(TaskType.READ_DAILY_LIFE, null, 300, 120, false, AT)
                        .timeTracked(TaskType.WRITE_EMAIL, null, 420, 420, false, AT),
                ActivityDay.start(USER, TODAY.minusDays(30), 30)
                        .timeTracked(TaskType.WRITE_EMAIL, null, 999, 420, false, AT));

        TimeReport report = TimeReport.of(days, TODAY.minusDays(6), TODAY.plusDays(1), TODAY, 30);

        assertThat(report.totalSeconds()).isEqualTo(1320);
        assertThat(report.bySection()).containsEntry(WeekSection.READING, 900).containsEntry(WeekSection.WRITING, 420);
        assertThat(report.byDay()).extracting(TimeReport.DayTime::date).containsExactly(TODAY.minusDays(2), TODAY);
        TimeReport.TaskTypeTime reading = report.byTaskType().stream()
                .filter(t -> t.taskType() == TaskType.READ_DAILY_LIFE)
                .findFirst()
                .orElseThrow();
        assertThat(reading.activeSeconds()).isEqualTo(900);
        assertThat(reading.pace().ratio()).isEqualTo(1.5);
        assertThat(reading.pace().attempts()).isEqualTo(2);
        assertThat(reading.pace().slowAttempts()).isEqualTo(1);
        assertThat(report.byDay().getLast().goalMet()).isFalse();
    }

    @Test
    @DisplayName("AC-16.03.1: the plan is minutesPerDay × days up to today; future days are not behind yet")
    void plannedTime() {
        TimeReport week = TimeReport.of(List.of(), TODAY.minusDays(3), TODAY.plusDays(4), TODAY, 30);

        assertThat(week.plannedSeconds()).isEqualTo(4 * 30 * 60);
        assertThat(week.totalSeconds()).isZero();
        assertThat(TimeReport.of(List.of(), TODAY.plusDays(1), TODAY.plusDays(3), TODAY, 30)
                        .plannedSeconds())
                .isZero();
        assertThat(TimeReport.of(List.of(), TODAY.minusDays(6), TODAY.plusDays(1), TODAY, TODAY, 30)
                        .plannedSeconds())
                .isEqualTo(30 * 60);
    }
}
