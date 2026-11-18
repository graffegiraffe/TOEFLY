package com.toefly.assessment.domain.evaluation;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An AI comment anchored to {@code [start, end)} of the version text in UTF-16 units (docs/03-domain-model.md §5.5).
 * A comment whose quote was not found keeps {@code start = end = null} and is shown as a general one.
 */
public record InlineAnnotation(
        UUID id,
        @Nullable Integer start,
        @Nullable Integer end,
        String anchorQuote,
        MistakeCategory category,
        String subcategory,
        Severity severity,
        String message,
        @Nullable String suggestion,
        @Nullable String grammarTopic,
        @Nullable String unitTitle,
        AnnotationStatus status) {

    public static final int MAX_MESSAGE = 400;
    public static final int MAX_SUGGESTION = 300;

    public InlineAnnotation {
        if ((start == null) != (end == null)) {
            throw new IllegalArgumentException("start and end come together");
        }
        if (start != null && (start < 0 || start >= end)) {
            throw new IllegalArgumentException("empty or negative range");
        }
        message = cut(message, MAX_MESSAGE);
        suggestion = suggestion == null || suggestion.isBlank() ? null : cut(suggestion, MAX_SUGGESTION);
    }

    public boolean anchored() {
        return start != null;
    }

    public InlineAnnotation withStatus(AnnotationStatus next) {
        return new InlineAnnotation(
                id,
                start,
                end,
                anchorQuote,
                category,
                subcategory,
                severity,
                message,
                suggestion,
                grammarTopic,
                unitTitle,
                next);
    }

    public boolean overlaps(int otherStart, int otherEnd) {
        return start != null && start < otherEnd && otherStart < end;
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
