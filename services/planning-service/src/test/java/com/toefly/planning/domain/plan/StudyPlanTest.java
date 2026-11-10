package com.toefly.planning.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StudyPlanTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @ParameterizedTest(name = "{0} days to the exam: {1} weeks")
    @CsvSource({"1, 1", "7, 1", "13, 1", "14, 2", "56, 8", "111, 15", "112, 16", "500, 16"})
    @DisplayName("AC-02.01.5: clamp(floor(days to the exam / 7), 1, 16) weeks")
    void length(int days, int weeks) {
        assertThat(StudyPlan.weeks(TODAY, TODAY.plusDays(days))).isEqualTo(weeks);
    }

    @Test
    @DisplayName("AC-02.01.4: 8 weeks without an exam date")
    void noExamDate() {
        assertThat(StudyPlan.weeks(TODAY, null)).isEqualTo(8);
    }

    @Test
    @DisplayName("AC-12.01.1: mock exams in weeks 4, 8 and the last one")
    void mockWeeks() {
        assertThat(plan(TODAY.plusDays(112)).mockExamWeeks()).containsExactly(4, 8, 16);
        assertThat(plan(TODAY.plusDays(7)).mockExamWeeks()).containsExactly(1);
        assertThat(plan(TODAY.plusDays(42)).mockExamWeeks()).containsExactly(4, 6);
    }

    @Test
    @DisplayName("AC-04.07.6: the last week of a plan with a later exam extends the plan by one week")
    void extension() {
        StudyPlan plan = plan(TODAY.plusDays(20)); // 2 weeks, the exam on day 21
        assertThat(plan.advance(1, TODAY.plusDays(7))).isEqualTo(2);
        assertThat(plan.advance(2, TODAY.plusDays(14))).isEqualTo(3);
        assertThat(plan.weeksTotal()).isEqualTo(3);
        assertThat(plan.advance(3, TODAY.plusDays(21))).isZero();
        StudyPlan open = plan(null);
        assertThat(open.advance(8, TODAY.plusDays(56))).isZero();
    }

    @Test
    @DisplayName("AC-02.05.1: a new goal revision changes the minutes; an older one is ignored")
    void goalRevision() {
        StudyPlan plan = plan(null);
        assertThat(plan.changeGoal(2, TODAY.plusDays(70), 90, ZoneId.of("UTC"), Map.of(), TODAY))
                .isTrue();
        assertThat(plan.minutesPerDay()).isEqualTo(90);
        assertThat(plan.weeksTotal()).isEqualTo(10);
        assertThat(plan.changeGoal(1, null, 30, ZoneId.of("UTC"), Map.of(), TODAY))
                .isFalse();
        assertThat(plan.daysToExam(TODAY)).isEqualTo(70);
    }

    @Test
    @DisplayName("the learner's day starts at local midnight")
    void calendar() {
        ZoneId kiritimati = ZoneId.of("Pacific/Kiritimati");
        Instant instant = Instant.parse("2026-11-10T10:30:00Z");
        assertThat(LearnerCalendar.today(instant, kiritimati)).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(LearnerCalendar.today(instant, ZoneId.of("Etc/GMT+12"))).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(LearnerCalendar.midnight(LocalDate.of(2026, 11, 11), kiritimati))
                .isEqualTo(Instant.parse("2026-11-10T10:00:00Z"));
    }

    private static StudyPlan plan(LocalDate exam) {
        return StudyPlan.create(
                UUID.randomUUID(), exam, TODAY, ZoneId.of("Europe/Minsk"), 60, Map.of(), 1, Instant.now());
    }
}
