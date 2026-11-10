package com.toefly.planning.domain.week;

/** Who put the task into the week. */
public enum TaskOrigin {
    /** The standard composition of the week (book tracks, the base counts of exam types). */
    SYSTEM,
    /** Extra sets picked for weak types by the planner or the agent. */
    AI,
    /** Added by the tutor; kept through rebalances. */
    TUTOR
}
