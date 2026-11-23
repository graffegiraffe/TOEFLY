package com.toefly.vocabulary.domain.fsrs;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * FSRS-5 scheduler of docs/03 §11.3–11.4, checked against the reference implementation (py-fsrs 5.1.3).
 *
 * <p>Days are counted in the learner's zone: the elapsed time {@code t} is the number of local midnights between the
 * last review and now (a review on the same local day uses the short-term formula), and a REVIEW card becomes due at
 * the start of the local day {@code interval} days later. Rounding of intervals is half-to-even, as in the reference.
 */
public final class FsrsScheduler {

    private final FsrsParameters p;

    public FsrsScheduler(FsrsParameters parameters) {
        this.p = Objects.requireNonNull(parameters, "parameters");
    }

    public FsrsParameters parameters() {
        return p;
    }

    /** Applies one grade (INV-VOC-03); {@code fuzzSeed} makes the fuzz deterministic for the card. */
    public ScheduledReview review(CardMemory card, Rating rating, Instant now, ZoneId zone, long fuzzSeed) {
        return preview(card, now, zone, fuzzSeed).get(rating);
    }

    /**
     * Outcome of each grade at {@code now}. In REVIEW the intervals of Hard, Good and Easy are ordered     * {@code hard < good < easy} (docs/03 §11.4) before the fuzz.
     */
    public Map<Rating, ScheduledReview> preview(CardMemory card, Instant now, ZoneId zone, long fuzzSeed) {
        Objects.requireNonNull(card, "card");
        double elapsed = elapsedDays(card, now, zone);
        Map<Rating, Next> next = new EnumMap<>(Rating.class);
        for (Rating rating : Rating.values()) {
            next.put(rating, step(card, rating, elapsed));
        }
        if (card.state() == CardState.REVIEW) {
            order(next);
        }
        Map<Rating, ScheduledReview> result = new EnumMap<>(Rating.class);
        for (Rating rating : Rating.values()) {
            result.put(rating, schedule(card, rating, next.get(rating), now, zone, elapsed, fuzzSeed));
        }
        return result;
    }

    /** R(t, S) = (1 + FACTOR · t / S)^DECAY. */
    public static double retrievability(double elapsedDays, double stability) {
        if (stability <= 0) {
            return 0;
        }
        return Math.pow(1 + FsrsParameters.FACTOR * elapsedDays / stability, FsrsParameters.DECAY);
    }

    /** Retrievability of the card at {@code now}; 0 for a card never reviewed. */
    public double retrievability(CardMemory card, Instant now, ZoneId zone) {
        if (card.lastReviewAt() == null) {
            return 0;
        }
        return retrievability(elapsedDays(card, now, zone), card.stability());
    }

    // ---- one grade ----

    /** State after a grade before the interval is fixed: {@code days > 0} for REVIEW, otherwise {@code delay}. */
    private record Next(CardState state, int step, double stability, double difficulty, int days, Duration delay) {}

    private Next step(CardMemory card, Rating rating, double elapsed) {
        return switch (card.state()) {
            case NEW -> learning(CardState.LEARNING, 0, initialStability(rating), initialDifficulty(rating), rating);
            case LEARNING -> {
                double[] sd = memory(card, rating, elapsed);
                yield learning(CardState.LEARNING, card.step(), sd[0], sd[1], rating);
            }
            case RELEARNING -> {
                double[] sd = memory(card, rating, elapsed);
                yield learning(CardState.RELEARNING, card.step(), sd[0], sd[1], rating);
            }
            case REVIEW -> {
                double[] sd = memory(card, rating, elapsed);
                if (rating == Rating.AGAIN) {
                    yield new Next(
                            CardState.RELEARNING,
                            0,
                            sd[0],
                            sd[1],
                            0,
                            steps(CardState.RELEARNING).get(0));
                }
                yield review(sd[0], sd[1]);
            }
        };
    }

    /** Learning and relearning steps; Good after the last step and Easy graduate to REVIEW. */
    private Next learning(CardState state, int step, double stability, double difficulty, Rating rating) {
        List<Duration> steps = steps(state);
        if (steps.isEmpty() || (step >= steps.size() && rating != Rating.AGAIN)) {
            return review(stability, difficulty);
        }
        return switch (rating) {
            case AGAIN -> new Next(state, 0, stability, difficulty, 0, steps.get(0));
            case HARD -> {
                Duration delay;
                if (step == 0 && steps.size() == 1) {
                    delay = steps.get(0).multipliedBy(3).dividedBy(2);
                } else if (step == 0) {
                    delay = steps.get(0).plus(steps.get(1)).dividedBy(2);
                } else {
                    delay = steps.get(step);
                }
                yield new Next(state, step, stability, difficulty, 0, delay);
            }
            case GOOD ->
                step + 1 >= steps.size()
                        ? review(stability, difficulty)
                        : new Next(state, step + 1, stability, difficulty, 0, steps.get(step + 1));
            case EASY -> review(stability, difficulty);
        };
    }

    private Next review(double stability, double difficulty) {
        return new Next(CardState.REVIEW, 0, stability, difficulty, nextInterval(stability), Duration.ZERO);
    }

    private List<Duration> steps(CardState state) {
        return state == CardState.RELEARNING ? p.relearningSteps() : p.learningSteps();
    }

    /** New stability and difficulty of a card already reviewed: short-term on the same day, long-term otherwise. */
    private double[] memory(CardMemory card, Rating rating, double elapsed) {
        double difficulty = nextDifficulty(card.difficulty(), rating);
        double stability;
        if (elapsed < 1) {
            stability = shortTermStability(card.stability(), rating);
        } else {
            double r = retrievability(elapsed, card.stability());
            stability = rating == Rating.AGAIN
                    ? forgetStability(card.difficulty(), card.stability(), r)
                    : recallStability(card.difficulty(), card.stability(), r, rating);
        }
        return new double[] {stability, difficulty};
    }

    private void order(Map<Rating, Next> next) {
        int max = p.maximumIntervalDays();
        Next hard = next.get(Rating.HARD);
        Next good = next.get(Rating.GOOD);
        Next easy = next.get(Rating.EASY);
        int hardDays = Math.min(hard.days(), good.days());
        int goodDays = Math.min(Math.max(good.days(), hardDays + 1), max);
        int easyDays = Math.min(Math.max(easy.days(), goodDays + 1), max);
        next.put(Rating.HARD, withDays(hard, hardDays));
        next.put(Rating.GOOD, withDays(good, goodDays));
        next.put(Rating.EASY, withDays(easy, easyDays));
    }

    private static Next withDays(Next next, int days) {
        return new Next(next.state(), next.step(), next.stability(), next.difficulty(), days, next.delay());
    }

    private ScheduledReview schedule(
            CardMemory card, Rating rating, Next next, Instant now, ZoneId zone, double elapsed, long fuzzSeed) {
        Instant due;
        Duration interval;
        if (next.state() == CardState.REVIEW) {
            int days = p.enableFuzz() ? fuzz(next.days(), fuzzSeed, card.reps()) : next.days();
            interval = Duration.ofDays(days);
            due = now.atZone(zone)
                    .toLocalDate()
                    .plusDays(days)
                    .atStartOfDay(zone)
                    .toInstant();
        } else {
            interval = next.delay();
            due = now.plus(next.delay());
        }
        int lapses = card.state() == CardState.REVIEW && rating == Rating.AGAIN ? card.lapses() + 1 : card.lapses();
        CardMemory after = new CardMemory(
                next.state(), next.step(), next.stability(), next.difficulty(), due, now, card.reps() + 1, lapses);
        return new ScheduledReview(rating, after, interval, elapsed);
    }

    // ---- formulas of docs/03 §11.3 ----

    double initialStability(Rating rating) {
        return Math.max(p.w(rating.grade() - 1), 0.1);
    }

    double initialDifficulty(Rating rating) {
        return clampDifficulty(p.w(4) - Math.exp(p.w(5) * (rating.grade() - 1)) + 1);
    }

    double nextDifficulty(double difficulty, Rating rating) {
        double delta = -p.w(6) * (rating.grade() - 3);
        double damped = difficulty + delta * (10 - difficulty) / 9;
        double reverted = p.w(7) * initialDifficulty(Rating.EASY) + (1 - p.w(7)) * damped;
        return clampDifficulty(reverted);
    }

    double shortTermStability(double stability, Rating rating) {
        return stability * Math.exp(p.w(17) * (rating.grade() - 3 + p.w(18)));
    }

    double recallStability(double difficulty, double stability, double r, Rating rating) {
        double hard = rating == Rating.HARD ? p.w(15) : 1;
        double easy = rating == Rating.EASY ? p.w(16) : 1;
        return stability
                * (1
                        + Math.exp(p.w(8))
                                * (11 - difficulty)
                                * Math.pow(stability, -p.w(9))
                                * (Math.exp((1 - r) * p.w(10)) - 1)
                                * hard
                                * easy);
    }

    /** Post-lapse stability, never above the short-term bound {@code S / e^(w17·w18)} (as in the reference). */
    double forgetStability(double difficulty, double stability, double r) {
        double longTerm = p.w(11)
                * Math.pow(difficulty, -p.w(12))
                * (Math.pow(stability + 1, p.w(13)) - 1)
                * Math.exp((1 - r) * p.w(14));
        double shortTerm = stability / Math.exp(p.w(17) * p.w(18));
        return Math.min(longTerm, shortTerm);
    }

    /** I(r, S) in whole days, rounded half-to-even, within [1, maximumInterval]. */
    int nextInterval(double stability) {
        double days =
                stability / FsrsParameters.FACTOR * (Math.pow(p.requestRetention(), 1 / FsrsParameters.DECAY) - 1);
        long rounded = (long) Math.rint(days);
        return (int) Math.min(Math.max(rounded, 1), p.maximumIntervalDays());
    }

    /**
     * Fuzz of docs/03 §11.2: intervals of 3 days and more move by up to ±5% (at least ±1 day); the offset is a
     * deterministic function of the card and its repetition number.
     */
    int fuzz(int days, long seed, int reps) {
        if (days < 3) {
            return days;
        }
        int delta = Math.max(1, (int) Math.round(days * 0.05));
        long mixed = mix(seed * 31 + reps);
        int offset = (int) Math.floorMod(mixed, 2L * delta + 1) - delta;
        return Math.min(Math.max(days + offset, 2), p.maximumIntervalDays());
    }

    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double clampDifficulty(double difficulty) {
        return Math.min(Math.max(difficulty, 1), 10);
    }

    private static double elapsedDays(CardMemory card, Instant now, ZoneId zone) {
        if (card.lastReviewAt() == null) {
            return 0;
        }
        LocalDate last = card.lastReviewAt().atZone(zone).toLocalDate();
        LocalDate today = now.atZone(zone).toLocalDate();
        return Math.max(0, ChronoUnit.DAYS.between(last, today));
    }
}
