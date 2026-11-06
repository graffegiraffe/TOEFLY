package com.toefly.content.domain.catalog;

import org.jspecify.annotations.Nullable;

/** The 16 task types (docs/00-README.md §5) with their section and ETS time norm (docs/03-domain-model.md §3.6). */
public enum TaskType {
    COMPLETE_THE_WORDS(Section.READING),
    READ_DAILY_LIFE(Section.READING),
    READ_ACADEMIC_PASSAGE(Section.READING),
    LISTEN_CHOOSE_RESPONSE(Section.LISTENING),
    LISTEN_CONVERSATION(Section.LISTENING),
    LISTEN_ANNOUNCEMENT(Section.LISTENING),
    LISTEN_ACADEMIC_TALK(Section.LISTENING),
    BUILD_A_SENTENCE(Section.WRITING),
    WRITE_EMAIL(Section.WRITING),
    WRITE_ACADEMIC_DISCUSSION(Section.WRITING),
    LISTEN_AND_REPEAT(Section.SPEAKING),
    TAKE_AN_INTERVIEW(Section.SPEAKING),
    GRAMMAR_EXERCISE(null),
    VOCAB_EXERCISE(null),
    LEGACY_READING(Section.READING),
    LEGACY_LISTENING(Section.LISTENING);

    private final @Nullable Section section;

    TaskType(@Nullable Section section) {
        this.section = section;
    }

    public @Nullable Section section() {
        return section;
    }

    public boolean legacy() {
        return this == LEGACY_READING || this == LEGACY_LISTENING;
    }

    public ExamFormat examFormat() {
        return legacy() ? ExamFormat.LEGACY : ExamFormat.TOEFL_2026;
    }

    /** ETS norm in seconds for a set with the given size and audio length. */
    public int etsNormSeconds(int itemCount, double audioSeconds) {
        int audio = (int) Math.ceil(audioSeconds);
        return switch (this) {
            case COMPLETE_THE_WORDS -> 180;
            case READ_DAILY_LIFE -> 60 * itemCount;
            case READ_ACADEMIC_PASSAGE -> 360;
            case LISTEN_CHOOSE_RESPONSE -> 150;
            case LISTEN_CONVERSATION, LISTEN_ANNOUNCEMENT -> audio + 60;
            case LISTEN_ACADEMIC_TALK -> audio + 120;
            case BUILD_A_SENTENCE -> 360;
            case WRITE_EMAIL -> 420;
            case WRITE_ACADEMIC_DISCUSSION -> 600;
            case LISTEN_AND_REPEAT -> 180;
            case TAKE_AN_INTERVIEW -> 240;
            case GRAMMAR_EXERCISE -> 45 * itemCount;
            case VOCAB_EXERCISE -> 30 * itemCount;
            case LEGACY_READING -> 75 * itemCount;
            case LEGACY_LISTENING -> audio + 30 * itemCount;
        };
    }

    /** {@code ceil(norm / 30) / 2}: minutes in steps of half a minute. */
    public static double estimatedMinutes(int etsNormSeconds) {
        return Math.ceil(etsNormSeconds / 30.0) / 2.0;
    }
}
