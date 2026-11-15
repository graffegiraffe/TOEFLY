package com.toefly.aigateway.domain.speech;

import org.jspecify.annotations.Nullable;

/** Settings of one pronunciation assessment (§2.2.3). */
public record AssessmentRequest(
        boolean scripted,
        @Nullable String referenceText,
        String locale,
        boolean prosody,
        boolean miscue,
        String granularity) {}
