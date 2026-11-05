package com.toefly.profile.domain.profile;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/** Time zone, explanation language and daily schedule of a user (INV-PRF-03). */
public record Preferences(
        ZoneId timezone, ExplanationLanguage explanationLanguage, int minutesPerDay, LocalTime preferredStudyTime) {

    /** Choices of the daily time step (AC-02.02.1). */
    public static final List<Integer> MINUTE_OPTIONS = List.of(15, 30, 45, 60, 90, 120, 180);

    public static final int DEFAULT_MINUTES = 60;
    public static final LocalTime DEFAULT_STUDY_TIME = LocalTime.of(19, 0);

    public Preferences {
        if (minutesPerDay < 15 || minutesPerDay > 180 || minutesPerDay % 5 != 0) {
            throw new InvalidProfileValueException("minutes-per-day", "Minutes per day are 15 to 180 in steps of 5.");
        }
        preferredStudyTime = preferredStudyTime.withSecond(0).withNano(0);
    }

    public static Preferences defaults(ZoneId timezone) {
        return new Preferences(timezone, ExplanationLanguage.EN, DEFAULT_MINUTES, DEFAULT_STUDY_TIME);
    }

    public static ZoneId zone(String id) {
        try {
            return ZoneId.of(id);
        } catch (DateTimeException e) {
            throw new InvalidProfileValueException("timezone", "Use an IANA time zone, for example Europe/Minsk.");
        }
    }

    public Preferences withTimezone(ZoneId zone) {
        return new Preferences(zone, explanationLanguage, minutesPerDay, preferredStudyTime);
    }

    public Preferences withExplanationLanguage(ExplanationLanguage language) {
        return new Preferences(timezone, language, minutesPerDay, preferredStudyTime);
    }

    public Preferences withSchedule(int minutes, LocalTime studyTime) {
        return new Preferences(timezone, explanationLanguage, minutes, studyTime);
    }
}
