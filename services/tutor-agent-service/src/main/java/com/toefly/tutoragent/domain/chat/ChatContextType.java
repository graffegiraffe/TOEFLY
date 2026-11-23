package com.toefly.tutoragent.domain.chat;

/** Screen the chat was opened from (docs/06 §4.5). */
public enum ChatContextType {
    GENERAL,
    WEEK,
    ATTEMPT_IN_PROGRESS,
    ATTEMPT_REVIEW,
    SUBMISSION_DRAFT,
    SUBMISSION_REVIEW,
    WEEKLY_REVIEW,
    VOCABULARY,
    GRAMMAR_UNIT;

    /** The task on the screen is not submitted: no answers, keys or rewritten responses (docs/06 §4.2, §4.8). */
    public boolean unsubmitted() {
        return this == ATTEMPT_IN_PROGRESS || this == SUBMISSION_DRAFT;
    }
}
