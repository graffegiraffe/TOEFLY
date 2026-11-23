package com.toefly.vocabulary.application.port.in;

import com.toefly.vocabulary.domain.entry.EntryOrigin;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** "Save word" (AC-15.01.2): the selected term, the sentence around it and where it was met. */
public record AddWordCommand(
        UUID userId,
        String term,
        @Nullable String context,
        @Nullable UUID itemSetId,
        @Nullable String sourceLabel,
        EntryOrigin origin) {}
