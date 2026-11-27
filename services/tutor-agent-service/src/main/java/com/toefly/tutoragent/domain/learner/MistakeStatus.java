package com.toefly.tutoragent.domain.learner;

/** Status of a subcategory (docs/06 §6.2). */
public enum MistakeStatus {
    ACTIVE,
    WATCH,
    RESOLVED;

    double rankingFactor() {
        return this == ACTIVE ? 1.0 : 0.5;
    }
}
