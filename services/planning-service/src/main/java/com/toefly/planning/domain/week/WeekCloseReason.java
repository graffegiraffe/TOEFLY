package com.toefly.planning.domain.week;

public enum WeekCloseReason {
    /** At least 70% done after day 7. */
    PROGRESS_70,
    /** Day 10 ended. */
    DAY_10,
    /** Finish week early at 100% before the end of day 7. */
    EARLY
}
