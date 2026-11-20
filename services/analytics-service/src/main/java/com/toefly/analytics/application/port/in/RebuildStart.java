package com.toefly.analytics.application.port.in;

/** The job of a rebuild request; {@code replayed} when an earlier request with the same key started it. */
public record RebuildStart(RebuildJob job, boolean replayed) {}
