package com.toefly.vocabulary.application.port.in;

import com.toefly.vocabulary.domain.entry.WordDetails;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The card shown for a selected word before saving (AC-15.01.1); {@code available} is false when no dictionary data
 * could be found (AI off or failing): the word can still be saved.
 */
public record WordCardView(
        String lemma,
        WordDetails details,
        boolean available,
        @Nullable UUID entryId,
        boolean pronunciation) {}
