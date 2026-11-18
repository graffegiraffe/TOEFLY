package com.toefly.assessment.domain.grading;

/** Rounding of scores and bands. */
public final class Scores {

    private Scores() {}

    /** Nearest 0.5, halves up: {@code floor(2x + 0.5) / 2} (docs/03-domain-model.md §7.6). */
    public static double roundHalf(double x) {
        return Math.floor(2 * x + 0.5) / 2;
    }

    public static double median(double a, double b, double c) {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
    }
}
