package com.toefly.practice.domain.ability;

/** Rasch (1PL) model (docs/03-domain-model.md §7.1, ADR-0014). */
public final class RaschModel {

    /** Theta and b are limited to [-4, 4] logits. */
    public static final double MIN_LOGIT = -4;

    public static final double MAX_LOGIT = 4;

    private RaschModel() {}

    /** Probability of a correct answer: {@code 1 / (1 + e^-(theta - b))}. */
    public static double probability(double theta, double b) {
        return 1 / (1 + Math.exp(-(theta - b)));
    }

    /** Fisher information of an item: {@code P (1 - P)}. */
    public static double information(double theta, double b) {
        double p = probability(theta, b);
        return p * (1 - p);
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
