package com.toefly.assessment.domain.evaluation;

/** Score and stamp of one grader pass (A, B or C) kept with the evaluation. */
public record PassRecord(String passTag, int score, String provider, String model, boolean degraded) {}
