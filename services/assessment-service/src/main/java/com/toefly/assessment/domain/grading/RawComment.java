package com.toefly.assessment.domain.grading;

import com.toefly.assessment.domain.evaluation.MistakeCategory;
import com.toefly.assessment.domain.evaluation.Severity;
import org.jspecify.annotations.Nullable;

/** A comment as the model wrote it: a quote of the response, not yet anchored to offsets. */
public record RawComment(
        String quote,
        int occurrence,
        MistakeCategory category,
        String subcategory,
        Severity severity,
        String comment,
        @Nullable String suggestion) {

    public RawComment {
        occurrence = Math.max(1, occurrence);
    }
}
