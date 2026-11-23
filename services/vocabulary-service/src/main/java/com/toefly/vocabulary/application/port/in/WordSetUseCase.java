package com.toefly.vocabulary.application.port.in;

import java.util.List;
import java.util.UUID;

/** Collins Vocabulary units as word sets (US-15.04). */
public interface WordSetUseCase {

    List<WordSetView> list(UUID userId);

    WordSetView get(UUID userId, UUID wordSetId);

    /** AC-15.04.2: adds the unit's words that are not in the dictionary yet. */
    Enrollment enroll(UUID userId, UUID wordSetId);

    /** Copies the approved units of content-service; returns the number of sets. */
    int sync();

    record Enrollment(UUID wordSetId, int added, int alreadyPresent) {}
}
