package com.toefly.tutoragent.domain.chat;

/** Intents of the router (docs/06 §4.3). */
public enum TutorIntent {
    EXPLAIN_MISTAKE,
    WRITING_FEEDBACK,
    SPEAKING_FEEDBACK,
    GRAMMAR_QUESTION,
    VOCABULARY_QUESTION,
    PLAN_QUESTION,
    PROGRESS_QUESTION,
    STRATEGY_QUESTION,
    MOTIVATION,
    PRACTICE_REQUEST,
    SOLVE_IN_PROGRESS,
    OFF_TOPIC
}
