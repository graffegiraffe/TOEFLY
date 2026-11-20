package com.toefly.analytics.domain.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.analytics.domain.common.TaskType;
import com.toefly.analytics.domain.common.WeekSection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActivityDayTest {

    static final UUID USER = UUID.randomUUID();
    static final LocalDate DAY = LocalDate.of(2026, 11, 20);
    static final Instant AT = Instant.parse("2026-11-20T10:00:00Z");

    @Test
    @DisplayName("AC-16.01.5, AC-16.02.1: closed attempts add their active time by week section and task type")
    void timeBySectionAndType() {
        ActivityDay day = ActivityDay.start(USER, DAY, 60)
                .timeTracked(TaskType.READ_DAILY_LIFE, WeekSection.READING, 120, 120, false, AT)
                .timeTracked(TaskType.COMPLETE_THE_WORDS, null, 200, 180, false, AT.plusSeconds(60))
                .timeTracked(TaskType.GRAMMAR_EXERCISE, WeekSection.GRAMMAR, 90, null, false, AT.plusSeconds(120));

        assertThat(day.activeSeconds()).isEqualTo(410);
        assertThat(day.secondsBySection())
                .containsEntry(WeekSection.READING, 320)
                .containsEntry(WeekSection.GRAMMAR, 90);
        assertThat(day.secondsByTaskType()).containsEntry(TaskType.COMPLETE_THE_WORDS, 200);
        assertThat(day.lastActiveAt()).isEqualTo(AT.plusSeconds(120));
        assertThat(day.pace()).doesNotContainKey(TaskType.GRAMMAR_EXERCISE);
    }

    @Test
    @DisplayName("AC-16.02.2: pace is active time over the ETS norm; more than 30% off is slower or faster")
    void paceAgainstNorm() {
        ActivityDay day = ActivityDay.start(USER, DAY, 60)
                .timeTracked(TaskType.READ_ACADEMIC_PASSAGE, null, 522, 360, false, AT)
                .timeTracked(TaskType.WRITE_EMAIL, null, 200, 420, false, AT)
                .timeTracked(TaskType.BUILD_A_SENTENCE, null, 380, 360, false, AT);

        PaceTally academic = day.pace().get(TaskType.READ_ACADEMIC_PASSAGE);
        assertThat(academic.ratio()).isEqualTo(1.45);
        assertThat(academic.verdict()).isEqualTo(PaceTally.Verdict.SLOWER);
        assertThat(academic.slowAttempts()).isEqualTo(1);
        assertThat(day.pace().get(TaskType.WRITE_EMAIL).verdict()).isEqualTo(PaceTally.Verdict.FASTER);
        assertThat(day.pace().get(TaskType.BUILD_A_SENTENCE).verdict()).isEqualTo(PaceTally.Verdict.ON_PACE);
        assertThat(PaceTally.NONE.verdict()).isEqualTo(PaceTally.Verdict.NO_NORM);
        assertThat(PaceTally.NONE.ratio()).isNull();
    }

    @Test
    @DisplayName("docs/03 §13.2: a suspicious time entry counts as time but not for the pace")
    void suspiciousSkipsPace() {
        ActivityDay day = ActivityDay.start(USER, DAY, 60).timeTracked(TaskType.WRITE_EMAIL, null, 900, 420, true, AT);

        assertThat(day.activeSeconds()).isEqualTo(900);
        assertThat(day.pace()).isEmpty();
    }

    @Test
    @DisplayName("AC-19.02.1, AC-16.02.4: the goal is met by active time of at least minutesPerDay")
    void goalByTime() {
        ActivityDay day =
                ActivityDay.start(USER, DAY, 10).timeTracked(TaskType.READ_DAILY_LIFE, null, 300, 120, false, AT);
        assertThat(day.goalMet()).isFalse();
        assertThat(day.goalProgress()).isEqualTo(0.5);

        ActivityDay met = day.timeTracked(TaskType.READ_DAILY_LIFE, null, 300, 120, false, AT);
        assertThat(met.goalMet()).isTrue();
        assertThat(met.goalProgress()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("AC-19.02.1, AC-19.02.2: all tasks of the day done meet the goal and fill the ring")
    void goalByDayPlan() {
        ActivityDay day = ActivityDay.start(USER, DAY, 60).taskDone(AT).withDayPlanDone();

        assertThat(day.tasksDone()).isEqualTo(1);
        assertThat(day.goalMet()).isTrue();
        assertThat(day.goalProgress()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("INV-ANL-03: a met goal stays met; time sums do not depend on the order of events")
    void commutativeAndSticky() {
        ActivityDay a = ActivityDay.start(USER, DAY, 5)
                .timeTracked(TaskType.READ_DAILY_LIFE, null, 200, 120, false, AT)
                .timeTracked(TaskType.WRITE_EMAIL, null, 150, 420, false, AT.plusSeconds(5))
                .taskDone(AT.plusSeconds(9));
        ActivityDay b = ActivityDay.start(USER, DAY, 5)
                .taskDone(AT.plusSeconds(9))
                .timeTracked(TaskType.WRITE_EMAIL, null, 150, 420, false, AT.plusSeconds(5))
                .timeTracked(TaskType.READ_DAILY_LIFE, null, 200, 120, false, AT);

        assertThat(a).isEqualTo(b);
        assertThat(a.goalMet()).isTrue();
        assertThat(ActivityDay.start(USER, DAY, 0).goalProgress()).isZero();
    }
}
