package com.toefly.planning.domain.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Days of the learner: every rule of the week counts in the time zone of the plan (docs/03 §10). */
public final class LearnerCalendar {

    private LearnerCalendar() {}

    public static LocalDate today(Instant now, ZoneId zone) {
        return LocalDate.ofInstant(now, zone);
    }

    /** The first instant of {@code date} in {@code zone}. */
    public static Instant midnight(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }
}
