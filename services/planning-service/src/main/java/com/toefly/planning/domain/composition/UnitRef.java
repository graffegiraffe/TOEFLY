package com.toefly.planning.domain.composition;

import java.util.UUID;

/** The next unit of a book track: New Round-Up 5, Collins Grammar or Collins Vocabulary. */
public record UnitRef(UUID unitId, String title) {}
