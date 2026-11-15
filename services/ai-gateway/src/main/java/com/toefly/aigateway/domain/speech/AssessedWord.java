package com.toefly.aigateway.domain.speech;

import java.util.List;

/** One word of an assessment with its error type and phonemes. */
public record AssessedWord(
        String word, double accuracy, String errorType, long offsetMs, long durationMs, List<Phoneme> phonemes) {}
