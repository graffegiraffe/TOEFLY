package com.toefly.vocabulary.application.port.in;

import com.toefly.vocabulary.domain.card.ReviewCard;
import com.toefly.vocabulary.domain.entry.VocabularyEntry;

/** An entry with its card. */
public record EntryRow(VocabularyEntry entry, ReviewCard card) {}
