package com.toefly.content.domain.catalog;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A book or site the items come from; shown on every task (Q7, ADR-0020). */
public record Source(
        UUID id,
        String code,
        String title,
        String publisher,
        @Nullable Integer year,
        @Nullable String url,
        ExamFormat examFormat,
        String attributionText,
        String extraJson) {

    public Source {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(examFormat, "examFormat");
        if (code.isBlank() || attributionText.isBlank()) {
            throw new IllegalArgumentException("source code and attribution must not be blank");
        }
    }

    public boolean legacy() {
        return examFormat == ExamFormat.LEGACY;
    }
}
