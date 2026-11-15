package com.toefly.aigateway.domain.speech;

import java.util.List;

/** Whisper transcription with word timestamps. */
public record Transcript(String text, List<TranscribedWord> words, double durationSec) {}
