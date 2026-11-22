package com.toefly.assessment.domain.submission;

import org.jspecify.annotations.Nullable;

/**
 * The model answer of the task of a submission with its source, kept with the submission so that the internal read
 * for the tutor agent answers from assessment_db only (docs/05-api-contracts.md §9.4).
 */
public record ModelAnswer(String text, @Nullable String source) {

    public ModelAnswer {
        if (text.isBlank()) {
            throw new IllegalArgumentException("a model answer has a text");
        }
    }
}
