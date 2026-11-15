package com.toefly.aigateway.domain.speech;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Normalised Azure pronunciation assessment; completeness only for SCRIPTED. */
public record Assessment(
        double accuracy,
        double fluency,
        @Nullable Double completeness,
        @Nullable Double prosody,
        double pronScore,
        String recognizedText,
        double durationSec,
        List<AssessedWord> words) {}
