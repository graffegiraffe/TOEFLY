package com.toefly.planning.domain.week;

public enum WeekStatus {
    DRAFT,
    AWAITING_TUTOR_APPROVAL,
    PUBLISHED,
    IN_PROGRESS,
    COMPLETED;

    /** The learner sees the week and its tasks change. */
    public boolean open() {
        return this == PUBLISHED || this == IN_PROGRESS;
    }

    /** The learner sees the week at all (AC-04.02.4). */
    public boolean visibleToStudent() {
        return this == PUBLISHED || this == IN_PROGRESS || this == COMPLETED;
    }
}
