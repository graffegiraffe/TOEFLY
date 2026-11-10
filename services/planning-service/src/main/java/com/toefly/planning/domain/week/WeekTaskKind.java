package com.toefly.planning.domain.week;

/** What a task of the week refers to (docs/03-domain-model.md §6.8, {@code refType}). */
public enum WeekTaskKind {
    ITEM_SET,
    GRAMMAR_UNIT,
    VOCABULARY_UNIT,
    VOCABULARY_REVIEW,
    TUTOR_MATERIAL,
    EXTERNAL_RESOURCE,
    MOCK_EXAM;

    /** Kinds the learner marks as done; item sets, reviews and mock exams close through events. */
    public boolean manuallyCompletable() {
        return this == GRAMMAR_UNIT || this == VOCABULARY_UNIT || this == TUTOR_MATERIAL || this == EXTERNAL_RESOURCE;
    }
}
