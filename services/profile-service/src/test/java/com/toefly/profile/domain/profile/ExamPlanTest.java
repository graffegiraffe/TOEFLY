package com.toefly.profile.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExamPlanTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Test
    @DisplayName("AC-02.01.3 a date in the past is rejected")
    void rejectsPastAndToday() {
        assertThatThrownBy(() -> ExamPlan.bookedOn(TODAY, TODAY))
                .isInstanceOf(InvalidProfileValueException.class)
                .hasMessage("Pick a date within the next 5 years");
        assertThatThrownBy(() -> ExamPlan.bookedOn(TODAY.minusDays(1), TODAY))
                .isInstanceOf(InvalidProfileValueException.class);
    }

    @Test
    @DisplayName("AC-02.01.3 a date more than five years ahead is rejected")
    void rejectsMoreThanAYearAhead() {
        assertThatThrownBy(() -> ExamPlan.bookedOn(TODAY.plusYears(5).plusDays(1), TODAY))
                .isInstanceOf(InvalidProfileValueException.class);
        assertThat(ExamPlan.bookedOn(TODAY.plusYears(5), TODAY).examDate()).isEqualTo(TODAY.plusYears(5));
    }

    @Test
    @DisplayName("AC-02.01.4 without a booked exam no date is stored")
    void notBookedHasNoDate() {
        assertThat(ExamPlan.notBooked().examDate()).isNull();
        assertThatThrownBy(() -> new ExamPlan(false, TODAY.plusDays(10)))
                .isInstanceOf(InvalidProfileValueException.class);
    }
}
