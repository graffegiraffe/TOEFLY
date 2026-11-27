package com.toefly.content.domain.catalog;

import org.jspecify.annotations.Nullable;

/**
 * Difficulty of a set relative to a reference difficulty b, for extra practice picked by the tutor agent
 * (docs/06-ai-agent.md §4.5): easier is below {@code b − 0.5}, harder above {@code b + 0.5}, the same within ±0.5.
 */
public enum RelativeDifficulty {
    EASIER,
    SAME,
    HARDER;

    /** Half width of the band of the same difficulty, in logits. */
    public static final double HALF_BAND = 0.5;

    /** The band of b around {@code reference}. */
    public Band around(double reference) {
        return switch (this) {
            case EASIER -> new Band(null, false, reference - HALF_BAND, false, reference);
            case SAME -> new Band(reference - HALF_BAND, true, reference + HALF_BAND, true, reference);
            case HARDER -> new Band(reference + HALF_BAND, false, null, false, reference);
        };
    }

    /**
     * Bounds of b; a null bound is open. {@code target} is the reference b: sets closest to it come first.
     */
    public record Band(
            @Nullable Double min,
            boolean minInclusive,
            @Nullable Double max,
            boolean maxInclusive,
            double target) {

        public boolean contains(double b) {
            boolean aboveMin = min == null || (minInclusive ? b >= min : b > min);
            boolean belowMax = max == null || (maxInclusive ? b <= max : b < max);
            return aboveMin && belowMax;
        }
    }
}
