package com.toefly.practice.domain.ability;

import com.toefly.practice.domain.attempt.AttemptMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Online Elo-like update of theta after a scored set and the MAP estimate used once a section has enough items
 * (docs/03-domain-model.md §7.3, §7.4).
 */
public final class RaschAbilityEstimator {

    static final double K0 = 0.40;
    static final double K_MIN = 0.06;
    static final double N0 = 25;
    static final double GAMMA = 0.97;
    static final double MAX_STEP = 0.8;
    static final double MAX_INFO = 44.4;
    static final double EXAM_FACTOR = 1.25;
    /** A repeated attempt of the same set counts with half the step (AC-06.01.7, §7.4). */
    static final double RETRY_FACTOR = 0.5;
    /** The MAP estimate replaces the online one when a section first reaches this many items (§7.3). */
    public static final int MAP_THRESHOLD = 8;

    private static final int FORGET_AFTER_DAYS = 14;
    private static final double FORGET_SCALE_DAYS = 60;
    private static final int NEWTON_ITERATIONS = 20;
    private static final double NEWTON_TOLERANCE = 0.001;

    private RaschAbilityEstimator() {}

    /**
     * Applies one scored set of {@code responses}. Items sharing a stimulus count with weight 1/sqrt(m)
     * ({@code independent = false}); EXAM attempts move theta 1.25 times more, retries half as much.
     */
    public static AbilityEstimate update(
            AbilityEstimate current,
            List<Response> responses,
            boolean independent,
            AttemptMode mode,
            boolean retry,
            Instant now) {
        if (responses.isEmpty()) {
            return current;
        }
        int m = responses.size();
        double info = forget(current, now);
        double w = independent ? 1 : 1 / Math.sqrt(m);
        double residual = 0;
        double information = 0;
        for (Response response : responses) {
            double p = RaschModel.probability(current.theta(), response.b());
            residual += response.x() - p;
            information += p * (1 - p);
        }
        double k = Math.max(K_MIN, K0 / (1 + current.n() / N0))
                * (mode == AttemptMode.EXAM ? EXAM_FACTOR : 1)
                * (retry ? RETRY_FACTOR : 1);
        double step = RaschModel.clamp(k * w * residual, -MAX_STEP, MAX_STEP);
        double theta = RaschModel.clamp(current.theta() + step, RaschModel.MIN_LOGIT, RaschModel.MAX_LOGIT);
        double nextInfo = RaschModel.clamp(GAMMA * info + w * information, AbilityEstimate.PRIOR_INFO, MAX_INFO);
        return new AbilityEstimate(
                current.userId(), current.section(), theta, nextInfo, current.n() + m, now, current.version());
    }

    /** Uncertainty grows back after a break longer than 14 days (§7.4). */
    static double forget(AbilityEstimate current, Instant now) {
        if (current.updatedAt() == null) {
            return current.info();
        }
        long days = Duration.between(current.updatedAt(), now).toDays();
        if (days <= FORGET_AFTER_DAYS) {
            return current.info();
        }
        return Math.max(
                AbilityEstimate.PRIOR_INFO, current.info() * Math.exp(-(days - FORGET_AFTER_DAYS) / FORGET_SCALE_DAYS));
    }

    /** MAP estimate with the N(0, 1.5^2) prior by Newton's method (§7.3). */
    public static MapEstimate map(List<Response> responses) {
        double prior = AbilityEstimate.PRIOR_INFO;
        double theta = 0;
        for (int i = 0; i < NEWTON_ITERATIONS; i++) {
            double gradient = -theta * prior;
            double information = prior;
            for (Response response : responses) {
                double p = RaschModel.probability(theta, response.b());
                gradient += response.x() - p;
                information += p * (1 - p);
            }
            double next = RaschModel.clamp(theta + gradient / information, RaschModel.MIN_LOGIT, RaschModel.MAX_LOGIT);
            boolean done = Math.abs(next - theta) < NEWTON_TOLERANCE;
            theta = next;
            if (done) {
                break;
            }
        }
        double information = prior;
        for (Response response : responses) {
            information += RaschModel.information(theta, response.b());
        }
        return new MapEstimate(theta, Math.min(MAX_INFO, information));
    }

    /** Theta and information of the MAP estimate. */
    public record MapEstimate(double theta, double info) {
        public double standardError() {
            return 1 / Math.sqrt(info);
        }
    }
}
