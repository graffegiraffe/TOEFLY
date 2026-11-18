package com.toefly.assessment.domain.evaluation;

/** Whether a comment of version n is fixed in version n + 1 (docs/03-domain-model.md §5.5). */
public enum AnnotationStatus {
    OPEN,
    FIXED,
    STILL_PRESENT
}
