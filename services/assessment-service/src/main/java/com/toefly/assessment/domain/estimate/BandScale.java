package com.toefly.assessment.domain.estimate;

/**
 * Constants {@code band-scale-v1} (docs/03-domain-model.md §8.1, §8.3): θ → band for Reading and Listening and the
 * share of the maximum → band for the rubric sections. Both are piecewise linear over the lower bounds of the bands.
 */
public final class BandScale {

    private static final double[] BANDS = {1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0};
    private static final double[] THETA = {-4.50, -3.40, -3.01, -2.46, -1.71, -0.58, 0.47, 1.16, 1.71, 2.46, 3.96};
    private static final double[] SHARE = {0.0, 0.10, 0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.78, 0.86, 0.94};

    private BandScale() {}

    /** B(θ). */
    public static double ofTheta(double theta) {
        return interpolate(THETA, theta);
    }

    /** B_p(p). */
    public static double ofShare(double share) {
        return interpolate(SHARE, share);
    }

    private static double interpolate(double[] nodes, double x) {
        if (x <= nodes[0]) {
            return BANDS[0];
        }
        if (x >= nodes[nodes.length - 1]) {
            return BANDS[BANDS.length - 1];
        }
        int k = 0;
        while (x >= nodes[k + 1]) {
            k++;
        }
        return BANDS[k] + 0.5 * (x - nodes[k]) / (nodes[k + 1] - nodes[k]);
    }

    /** CEFR of a band (docs/03-domain-model.md §8.5). */
    public static String cefr(double band) {
        if (band >= 6.0) {
            return "C2";
        }
        if (band >= 5.0) {
            return "C1";
        }
        if (band >= 4.0) {
            return "B2";
        }
        if (band >= 3.0) {
            return "B1";
        }
        return band >= 2.0 ? "A2" : "A1";
    }

    private static final int[] COMPARABLE_LOW = {0, 11, 22, 33, 44, 58, 72, 84, 95, 105, 114};

    /** Lower bound of the reference 0–120 scale for a band of the 0.5 grid. */
    public static int comparableLow(double band) {
        return COMPARABLE_LOW[index(band)];
    }

    /** Upper bound of the reference 0–120 scale for a band of the 0.5 grid. */
    public static int comparableHigh(double band) {
        int i = index(band);
        return i == COMPARABLE_LOW.length - 1 ? 120 : COMPARABLE_LOW[i + 1] - 1;
    }

    private static int index(double band) {
        int i = (int) Math.round((band - 1.0) * 2);
        return Math.max(0, Math.min(COMPARABLE_LOW.length - 1, i));
    }
}
