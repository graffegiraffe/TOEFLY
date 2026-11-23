package com.toefly.vocabulary.domain.fsrs;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Scheduler settings of docs/03 §11.2. {@link #v1()} is {@code fsrs-params-v1}: the FSRS-5 defaults of the reference
 * implementation, 90% retention, steps 1 and 10 minutes, relearning 10 minutes, 365 days at most.
 */
public record FsrsParameters(
        String version,
        double[] w,
        double requestRetention,
        int maximumIntervalDays,
        List<Duration> learningSteps,
        List<Duration> relearningSteps,
        boolean enableFuzz) {

    public static final double DECAY = -0.5;
    /** {@code 0.9^(1/DECAY) − 1 = 19/81}, so that R(S, S) = 0.9. */
    public static final double FACTOR = Math.pow(0.9, 1 / DECAY) - 1;

    private static final double[] W_V1 = {
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192, 1.01925, 1.9395, 0.11,
        0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621
    };

    public FsrsParameters {
        Objects.requireNonNull(version, "version");
        if (w.length != 19) {
            throw new IllegalArgumentException("FSRS-5 needs 19 weights, got " + w.length);
        }
        w = w.clone();
        learningSteps = List.copyOf(learningSteps);
        relearningSteps = List.copyOf(relearningSteps);
        if (requestRetention <= 0 || requestRetention >= 1) {
            throw new IllegalArgumentException("requestRetention must be in (0, 1)");
        }
        if (maximumIntervalDays < 1) {
            throw new IllegalArgumentException("maximumIntervalDays must be positive");
        }
    }

    public static FsrsParameters v1() {
        return new FsrsParameters(
                "fsrs-params-v1",
                W_V1,
                0.9,
                365,
                List.of(Duration.ofMinutes(1), Duration.ofMinutes(10)),
                List.of(Duration.ofMinutes(10)),
                true);
    }

    public FsrsParameters withoutFuzz() {
        return new FsrsParameters(
                version, w, requestRetention, maximumIntervalDays, learningSteps, relearningSteps, false);
    }

    public double w(int index) {
        return w[index];
    }

    @Override
    public double[] w() {
        return w.clone();
    }
}
