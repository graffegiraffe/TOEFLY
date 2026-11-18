package com.toefly.assessment.domain.estimate;

import org.jspecify.annotations.Nullable;

/**
 * Estimate of one section: READY with a point and an 80% range, or NEED_MORE_DATA with what is missing
 * ({@code remaining} items or submissions; docs/03-domain-model.md §8.6).
 */
public record SectionEstimate(
        Section section,
        boolean ready,
        @Nullable Double point,
        @Nullable Double continuous,
        @Nullable Double low,
        @Nullable Double high,
        @Nullable Double continuousLow,
        @Nullable Double continuousHigh,
        int n,
        int remaining) {

    public static SectionEstimate needMoreData(Section section, int n, int remaining) {
        return new SectionEstimate(section, false, null, null, null, null, null, null, n, Math.max(0, remaining));
    }
}
