package com.toefly.tutoragent.domain.chat;

import com.toefly.tutoragent.domain.learner.ExplanationLanguage;

/** Fixed replies that need no model (docs/06 §3.6). */
public final class Replies {

    private Replies() {}

    public static String offTopic(ExplanationLanguage language) {
        return language == ExplanationLanguage.RU
                ? "I'm here to help you prepare for TOEFL. Я помогаю только с английским и подготовкой к TOEFL. "
                        + "Можем превратить эту тему в практику: напишите о ней 3-4 предложения по-английски, и я их разберу."
                : "I'm here to help you prepare for TOEFL, so I can't help with that. We can turn the topic into "
                        + "practice: write 3-4 sentences about it in English and I'll review them.";
    }

    public static String solveBeforeSubmit(ExplanationLanguage language) {
        return language == ExplanationLanguage.RU
                ? "Я не могу дать ответ на задание, пока оно не сдано. Давайте разберём стратегию: что именно "
                        + "спрашивает вопрос и какая часть текста к нему относится? После сдачи я объясню всё полностью."
                : "I can't give the answer to a task you haven't submitted yet. Let's work on the strategy: what "
                        + "exactly does the question ask, and which part of the text is it about? After you submit, "
                        + "I'll explain everything in full.";
    }

    public static String refusal(ExplanationLanguage language) {
        return language == ExplanationLanguage.RU
                ? "Я не могу помочь с этим. Давайте вернёмся к подготовке к TOEFL."
                : "I can't help with that. Let's get back to your TOEFL preparation.";
    }
}
