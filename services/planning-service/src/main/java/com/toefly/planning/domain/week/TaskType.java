package com.toefly.planning.domain.week;

import java.util.List;
import java.util.Locale;

/** Task types of the canon (docs/00-README.md §5.1) with their section of the week. */
public enum TaskType {
    COMPLETE_THE_WORDS(WeekSection.READING, "Complete the Words"),
    READ_DAILY_LIFE(WeekSection.READING, "Read in Daily Life"),
    READ_ACADEMIC_PASSAGE(WeekSection.READING, "Academic passage"),
    LISTEN_CHOOSE_RESPONSE(WeekSection.LISTENING, "Choose a Response"),
    LISTEN_CONVERSATION(WeekSection.LISTENING, "Conversation"),
    LISTEN_ANNOUNCEMENT(WeekSection.LISTENING, "Announcement"),
    LISTEN_ACADEMIC_TALK(WeekSection.LISTENING, "Academic talk"),
    BUILD_A_SENTENCE(WeekSection.WRITING, "Build a Sentence"),
    WRITE_EMAIL(WeekSection.WRITING, "Email"),
    WRITE_ACADEMIC_DISCUSSION(WeekSection.WRITING, "Academic discussion"),
    LISTEN_AND_REPEAT(WeekSection.SPEAKING, "Listen and Repeat"),
    TAKE_AN_INTERVIEW(WeekSection.SPEAKING, "Take an Interview"),
    GRAMMAR_EXERCISE(WeekSection.GRAMMAR, "Grammar exercise"),
    VOCAB_EXERCISE(WeekSection.VOCABULARY, "Vocabulary exercise"),
    LEGACY_READING(WeekSection.READING, "Legacy reading"),
    LEGACY_LISTENING(WeekSection.LISTENING, "Legacy listening");

    /** The 12 exam types of the January 2026 format, in the order of the week table (docs/02 §4.0). */
    public static final List<TaskType> EXAM = List.of(
            COMPLETE_THE_WORDS,
            READ_DAILY_LIFE,
            READ_ACADEMIC_PASSAGE,
            LISTEN_CHOOSE_RESPONSE,
            LISTEN_CONVERSATION,
            LISTEN_ANNOUNCEMENT,
            LISTEN_ACADEMIC_TALK,
            BUILD_A_SENTENCE,
            WRITE_EMAIL,
            WRITE_ACADEMIC_DISCUSSION,
            LISTEN_AND_REPEAT,
            TAKE_AN_INTERVIEW);

    private final WeekSection section;
    private final String label;

    TaskType(WeekSection section, String label) {
        this.section = section;
        this.label = label;
    }

    public WeekSection section() {
        return section;
    }

    /** Short English name used in task titles, for example {@code "Academic passage"}. */
    public String label() {
        return label;
    }

    /** Writing and Speaking answers that a person or AI grades; the rest is machine-scored. */
    public boolean open() {
        return this == WRITE_EMAIL
                || this == WRITE_ACADEMIC_DISCUSSION
                || this == LISTEN_AND_REPEAT
                || this == TAKE_AN_INTERVIEW;
    }

    public boolean legacy() {
        return this == LEGACY_READING || this == LEGACY_LISTENING;
    }

    /** The name of the type in a sentence, for example {@code "academic passage"}. */
    public String lowerLabel() {
        return label.toLowerCase(Locale.ROOT);
    }
}
