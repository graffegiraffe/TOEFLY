package com.toefly.profile.domain.profile;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Exam date of the learner; without a booked exam no date is stored (INV-PRF-02, AC-02.01.4). */
public record ExamPlan(boolean booked, @Nullable LocalDate examDate) {

    public static final int MAX_DAYS_AHEAD = 365;

    public ExamPlan {
        if (booked != (examDate != null)) {
            throw new InvalidProfileValueException(
                    "exam-date", "A booked exam needs a date, an unbooked one has none.");
        }
    }

    public static ExamPlan notBooked() {
        return new ExamPlan(false, null);
    }

    /** A date entered today must lie after today and at most 365 days ahead (AC-02.01.3). */
    public static ExamPlan bookedOn(LocalDate examDate, LocalDate today) {
        if (!examDate.isAfter(today) || examDate.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new InvalidProfileValueException("exam-date", "Pick a date within the next 12 months");
        }
        return new ExamPlan(true, examDate);
    }
}
