package com.toefly.analytics.domain.activity;

import org.jspecify.annotations.Nullable;

/**
 * Active time against the ETS norm for one task type: {@code paceRatio = activeSeconds / normSeconds}; an attempt more
 * than 30% off the norm is slow or fast (docs/03 §13.4, AC-16.02.2).
 */
public record PaceTally(int activeSeconds, int normSeconds, int attempts, int slowAttempts, int fastAttempts) {

    /** Allowed deviation from the exam pace before it is highlighted. */
    public static final double TOLERANCE = 0.30;

    public static final PaceTally NONE = new PaceTally(0, 0, 0, 0, 0);

    public PaceTally plus(int active, int norm) {
        double ratio = norm == 0 ? 1 : (double) active / norm;
        return new PaceTally(
                activeSeconds + active,
                normSeconds + norm,
                attempts + 1,
                slowAttempts + (ratio > 1 + TOLERANCE ? 1 : 0),
                fastAttempts + (ratio < 1 - TOLERANCE ? 1 : 0));
    }

    public PaceTally plus(PaceTally other) {
        return new PaceTally(
                activeSeconds + other.activeSeconds,
                normSeconds + other.normSeconds,
                attempts + other.attempts,
                slowAttempts + other.slowAttempts,
                fastAttempts + other.fastAttempts);
    }

    /** Ratio of the time spent to the norm, rounded to hundredths; {@code null} without a norm. */
    public @Nullable Double ratio() {
        if (normSeconds == 0) {
            return null;
        }
        return Math.round(100.0 * activeSeconds / normSeconds) / 100.0;
    }

    public Verdict verdict() {
        Double ratio = ratio();
        if (ratio == null) {
            return Verdict.NO_NORM;
        }
        if (ratio > 1 + TOLERANCE) {
            return Verdict.SLOWER;
        }
        if (ratio < 1 - TOLERANCE) {
            return Verdict.FASTER;
        }
        return Verdict.ON_PACE;
    }

    public enum Verdict {
        ON_PACE,
        SLOWER,
        FASTER,
        NO_NORM
    }
}
