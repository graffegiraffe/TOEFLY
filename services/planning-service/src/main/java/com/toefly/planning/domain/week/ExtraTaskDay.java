package com.toefly.planning.domain.week;

/** The day Toefly Tutor asks for an extra task ({@code assignExtraTask}, docs/06-ai-agent.md §4.5). */
public enum ExtraTaskDay {
    TODAY,
    TOMORROW,
    /** The first day from today with room for the task. */
    NEXT_FREE_DAY
}
