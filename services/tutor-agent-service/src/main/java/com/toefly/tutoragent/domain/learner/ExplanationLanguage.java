package com.toefly.tutoragent.domain.learner;

/** Language of explanations, hints and motivation (docs/06 §4.1); examples and corrections stay in English. */
public enum ExplanationLanguage {
    EN,
    RU;

    /** Reads a profile or request value; anything other than RU means English. */
    public static ExplanationLanguage of(String value) {
        return "RU".equalsIgnoreCase(value) ? RU : EN;
    }
}
