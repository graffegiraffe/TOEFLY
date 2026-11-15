package com.toefly.aigateway.domain.speech;

/** Accuracy of one phoneme, 0–100. */
public record Phoneme(String phoneme, double accuracy) {}
