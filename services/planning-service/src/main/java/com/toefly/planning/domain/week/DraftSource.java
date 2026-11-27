package com.toefly.planning.domain.week;

/** How the tasks of a week were chosen and laid out (docs/06-ai-agent.md §5.2). */
public enum DraftSource {
    /** The rules of the week composition (docs/03 §10.2), before any weekly review. */
    RULES,
    /** WeekGenerator: the model chose and laid out candidates, the draft passed the validation. */
    AI,
    /** WeekGenerator failed twice or ai-gateway was down: GreedyWeekPacker with template texts. */
    AI_FALLBACK
}
