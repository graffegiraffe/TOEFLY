package com.toefly.planning.domain.week;

/** Section of a week: the four exam sections, the two book tracks and external resources (docs/00-README.md §5.2). */
public enum WeekSection {
    READING,
    LISTENING,
    WRITING,
    SPEAKING,
    VOCABULARY,
    GRAMMAR,
    RESOURCES;

    /** One of the four sections of the exam. */
    public boolean exam() {
        return this == READING || this == LISTENING || this == WRITING || this == SPEAKING;
    }
}
