package com.toefly.planning.domain.week;

/** Why a task was skipped (docs/00-README.md §5.2). */
public enum WeekTaskSkipReason {
    USER_SKIPPED,
    REBALANCE,
    TUTOR,
    WEEK_CLOSE,
    NO_ENTITLEMENT;

    /** Tasks skipped for these reasons leave the denominator of the progress (AC-04.02.3). */
    public boolean excludedFromProgress() {
        return this == REBALANCE || this == TUTOR || this == NO_ENTITLEMENT;
    }
}
