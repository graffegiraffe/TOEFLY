package com.toefly.planning.domain.composition;

/** θ → band of Reading and Listening, constants {@code band-scale-v1} (docs/03-domain-model.md §8.1). */
public final class BandScale {

    private static final double[] BANDS = {1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0};
    private static final double[] THETA = {-4.50, -3.40, -3.01, -2.46, -1.71, -0.58, 0.47, 1.16, 1.71, 2.46, 3.96};

    private BandScale() {}

    /** Continuous band, piecewise linear between the nodes of the table. */
    public static double band(double theta) {
        if (theta <= THETA[0]) {
            return BANDS[0];
        }
        if (theta >= THETA[THETA.length - 1]) {
            return BANDS[BANDS.length - 1];
        }
        int k = 0;
        while (theta >= THETA[k + 1]) {
            k++;
        }
        return BANDS[k] + 0.5 * (theta - THETA[k]) / (THETA[k + 1] - THETA[k]);
    }
}
