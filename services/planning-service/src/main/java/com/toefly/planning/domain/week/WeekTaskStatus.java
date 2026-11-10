package com.toefly.planning.domain.week;

public enum WeekTaskStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    SKIPPED;

    /** Still to do: counted in Today and in the lag of the week. */
    public boolean open() {
        return this == PENDING || this == IN_PROGRESS;
    }
}
