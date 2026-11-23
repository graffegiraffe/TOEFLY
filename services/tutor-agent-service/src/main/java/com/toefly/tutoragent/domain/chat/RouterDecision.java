package com.toefly.tutoragent.domain.chat;

import com.toefly.tutoragent.domain.learner.ExplanationLanguage;

/** Output of the router (docs/06 §4.3); {@link #fallback} when the router model did not answer. */
public record RouterDecision(
        TutorIntent intent,
        ExplanationLanguage language,
        boolean addWord,
        boolean showAnswer,
        boolean assignTask,
        boolean escalate) {

    /** Without the router the turn is a general strategy question with the default tools. */
    public static RouterDecision fallback(ExplanationLanguage language) {
        return new RouterDecision(TutorIntent.STRATEGY_QUESTION, language, false, false, false, false);
    }
}
