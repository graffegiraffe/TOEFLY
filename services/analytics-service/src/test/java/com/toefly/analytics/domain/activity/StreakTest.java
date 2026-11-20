package com.toefly.analytics.domain.activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StreakTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    @Test
    @DisplayName("AC-19.01.1: every day with the goal met before today extends the streak; a missed day resets it")
    void consecutiveDaysCount() {
        Streak streak = Streak.of(
                Set.of(TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(3), TODAY.minusDays(5)), TODAY, 30);

        assertThat(streak.current()).isEqualTo(3);
        assertThat(streak.todayDone()).isFalse();

        Streak broken = Streak.of(Set.of(TODAY.minusDays(2), TODAY.minusDays(3)), TODAY, 30);
        assertThat(broken.current()).isZero();
        assertThat(broken.longest()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-19.01.2: the goal met today counts at once with \"Today done\"")
    void todayCountsImmediately() {
        Streak streak = Streak.of(Set.of(TODAY, TODAY.minusDays(1)), TODAY, 30);

        assertThat(streak.current()).isEqualTo(2);
        assertThat(streak.todayDone()).isTrue();
    }

    @Test
    @DisplayName("AC-19.01.3: current and record streak, and a calendar of the last 30 days")
    void calendarAndRecord() {
        Set<LocalDate> done = Set.of(
                TODAY.minusDays(40),
                TODAY.minusDays(39),
                TODAY.minusDays(38),
                TODAY.minusDays(37),
                TODAY.minusDays(1),
                TODAY);

        Streak streak = Streak.of(done, TODAY, 45);

        assertThat(streak.longest()).isEqualTo(4);
        assertThat(streak.current()).isEqualTo(2);
        assertThat(streak.goalMinutes()).isEqualTo(45);
        List<Streak.CalendarDay> days = streak.lastDays();
        assertThat(days).hasSize(30);
        assertThat(days.getFirst().date()).isEqualTo(TODAY.minusDays(29));
        assertThat(days.getLast()).isEqualTo(new Streak.CalendarDay(TODAY, true));
        assertThat(days.stream().filter(Streak.CalendarDay::done)).hasSize(2);
    }

    @Test
    @DisplayName("INV-ANL-03: the record is never below the current streak and ignores days after today")
    void longestCoversCurrent() {
        Streak streak = Streak.of(Set.of(TODAY, TODAY.plusDays(1), TODAY.plusDays(2)), TODAY, 30);

        assertThat(streak.current()).isEqualTo(1);
        assertThat(streak.longest()).isEqualTo(1);
        assertThat(Streak.of(Set.of(), TODAY, 30).current()).isZero();
    }
}
