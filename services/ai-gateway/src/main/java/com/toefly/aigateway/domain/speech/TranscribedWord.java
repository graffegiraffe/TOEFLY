package com.toefly.aigateway.domain.speech;

import org.jspecify.annotations.Nullable;

/** A recognised word with its timestamps in seconds. */
public record TranscribedWord(
        String word, double start, double end, @Nullable Double probability) {}
