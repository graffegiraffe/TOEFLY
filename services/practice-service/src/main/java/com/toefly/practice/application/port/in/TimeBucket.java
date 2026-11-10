package com.toefly.practice.application.port.in;

/** Active and estimated seconds of one bucket of a time summary. */
public record TimeBucket(String key, int activeSeconds, int estimatedSeconds, int attempts) {}
