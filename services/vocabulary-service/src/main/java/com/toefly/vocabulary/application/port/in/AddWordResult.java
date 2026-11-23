package com.toefly.vocabulary.application.port.in;

/** The saved entry; {@code created} is false for "Already in your dictionary" (AC-15.01.3). */
public record AddWordResult(EntryRow row, boolean created) {}
