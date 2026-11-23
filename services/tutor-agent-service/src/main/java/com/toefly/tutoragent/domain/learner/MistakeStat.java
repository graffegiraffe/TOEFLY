package com.toefly.tutoragent.domain.learner;

import java.time.Instant;
import java.util.List;

/** Statistics of one subcategory in the learner model (docs/06 §6.1). */
public record MistakeStat(
        String subcategory,
        MistakeCategory category,
        double weight,
        double errorRate,
        MistakeTrend trend,
        MistakeStatus status,
        double priority,
        int occurrences28Days,
        int occurrencesLast7Days,
        int opportunities28Days,
        Instant lastSeenAt,
        List<String> unitRefs) {

    public MistakeStat {
        unitRefs = List.copyOf(unitRefs);
    }
}
