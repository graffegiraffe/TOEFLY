package com.toefly.vocabulary.application.port.in;

import java.util.UUID;

/** An entry whose card lapsed, for the tutor's summary (docs/05 §9.3.4). */
public record LapsedEntry(UUID entryId, String lemma, int lapses) {}
