package com.toefly.content.application.seed;

import com.toefly.content.domain.catalog.ExamFormat;
import org.jspecify.annotations.Nullable;

/** The {@code SOURCE} object of a seed bundle. */
public record SeedSourceInfo(
        String sourceCode,
        String title,
        String publisher,
        @Nullable Integer year,
        @Nullable String url,
        ExamFormat examFormat,
        String attributionText,
        String extraJson) {}
