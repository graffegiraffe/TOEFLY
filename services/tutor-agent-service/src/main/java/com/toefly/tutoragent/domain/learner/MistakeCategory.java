package com.toefly.tutoragent.domain.learner;

/** Canonical mistake categories with the impact factor of the top-mistakes ranking (docs/06 §6.2). */
public enum MistakeCategory {
    GRAMMAR(1.0),
    VOCABULARY(1.0),
    SPELLING(0.7),
    COHERENCE(1.15),
    TASK_RESPONSE(1.3),
    PRONUNCIATION(1.0),
    FLUENCY(1.0),
    COMPREHENSION(1.0),
    TIME_MANAGEMENT(0.9);

    private final double impact;

    MistakeCategory(double impact) {
        this.impact = impact;
    }

    public double impact() {
        return impact;
    }

    /** Category of a subcategory code {@code <CATEGORY>.<NAME>}. */
    public static MistakeCategory ofSubcategory(String code) {
        int dot = code.indexOf('.');
        return valueOf(dot < 0 ? code : code.substring(0, dot));
    }
}
