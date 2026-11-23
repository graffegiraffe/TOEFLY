package com.toefly.analytics.application.port.in;

/**
 * Named periods of the time report for the {@code getTimeStats} tool of the tutor agent (docs/06-ai-agent.md §4.5),
 * counted in days of the learner's time zone up to today inclusive.
 */
public enum ReportPeriod {
    /** Today only. */
    TODAY,
    /** From the first day of the current study week, or the last seven days without one (as "time this week"). */
    THIS_WEEK,
    /** The last 28 days. */
    LAST_4_WEEKS
}
