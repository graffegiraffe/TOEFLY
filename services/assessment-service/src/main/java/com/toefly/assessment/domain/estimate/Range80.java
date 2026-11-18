package com.toefly.assessment.domain.estimate;

import com.toefly.assessment.domain.grading.Scores;

/** The 80% range on the band scale with its rounding rules (docs/03-domain-model.md §7.6). */
public record Range80(double low, double high) {

    public static final double Z = 1.28;

    public static Range80 of(double lo, double point, double hi) {
        double low = Scores.roundHalf(lo);
        double high = Scores.roundHalf(hi);
        if (low == high) {
            if (point >= low) {
                high = low + 0.5;
            } else {
                low = high - 0.5;
            }
        }
        low = Math.max(1.0, low);
        high = Math.min(6.0, high);
        if (low == 6.0 && high == 6.0) {
            low = 5.5;
        }
        return new Range80(low, high);
    }

    public double width() {
        return high - low;
    }
}
