package com.toefly.practice.domain.attempt;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An item as the attempt pinned it at start: identifier, position, difficulty and maximum score
 * (INV-ATT-05, docs/03-domain-model.md §6.5).
 */
public record PinnedItem(UUID itemId, int ordinal, @Nullable Double b, double maxScore) {}
