package com.toefly.practice.domain.time;

/** What the active time belongs to (docs/03-domain-model.md §13.3). */
public enum ContextType {
    ATTEMPT,
    GRAMMAR_STEP,
    MATERIAL_VIEW,
    VOCAB_REVIEW,
    EXTERNAL_RESOURCE
}
