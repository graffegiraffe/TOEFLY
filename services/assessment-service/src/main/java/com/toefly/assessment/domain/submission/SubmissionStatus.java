package com.toefly.assessment.domain.submission;

/** Canonical status of a version (docs/00-README.md §5.2); the submission shows the status of its current version. */
public enum SubmissionStatus {
    DRAFT,
    SUBMITTED,
    EVALUATING,
    EVALUATED,
    EVALUATION_DEFERRED,
    TUTOR_REVIEWED;

    public boolean evaluated() {
        return this == EVALUATED || this == TUTOR_REVIEWED;
    }
}
