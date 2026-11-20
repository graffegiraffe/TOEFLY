package com.toefly.analytics.domain.common;

import org.jspecify.annotations.Nullable;

/** Task types of the canon (docs/00-README.md §5.1) with the column of the week they count in. */
public enum TaskType {
    COMPLETE_THE_WORDS(WeekSection.READING),
    READ_DAILY_LIFE(WeekSection.READING),
    READ_ACADEMIC_PASSAGE(WeekSection.READING),
    LISTEN_CHOOSE_RESPONSE(WeekSection.LISTENING),
    LISTEN_CONVERSATION(WeekSection.LISTENING),
    LISTEN_ANNOUNCEMENT(WeekSection.LISTENING),
    LISTEN_ACADEMIC_TALK(WeekSection.LISTENING),
    BUILD_A_SENTENCE(WeekSection.WRITING),
    WRITE_EMAIL(WeekSection.WRITING),
    WRITE_ACADEMIC_DISCUSSION(WeekSection.WRITING),
    LISTEN_AND_REPEAT(WeekSection.SPEAKING),
    TAKE_AN_INTERVIEW(WeekSection.SPEAKING),
    GRAMMAR_EXERCISE(WeekSection.GRAMMAR),
    VOCAB_EXERCISE(WeekSection.VOCABULARY),
    LEGACY_READING(WeekSection.READING),
    LEGACY_LISTENING(WeekSection.LISTENING);

    private final WeekSection weekSection;

    TaskType(WeekSection weekSection) {
        this.weekSection = weekSection;
    }

    public WeekSection weekSection() {
        return weekSection;
    }

    /** The canon name, or {@code null} for {@code UNKNOWN} and names of a newer schema. */
    public static @Nullable TaskType parse(@Nullable String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
