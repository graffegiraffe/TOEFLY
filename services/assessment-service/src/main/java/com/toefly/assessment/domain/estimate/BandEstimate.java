package com.toefly.assessment.domain.estimate;

import com.toefly.assessment.domain.grading.Scores;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Band estimate of a learner (docs/03-domain-model.md §8): sections from θ (Reading, Listening) and from rubric
 * scores (Writing; Speaking from phase 6), overall only when all four sections are READY (INV-EST-02).
 */
public record BandEstimate(
        List<SectionEstimate> sections, @Nullable Overall overall, Instant asOf) {

    public record Overall(double point, double low, double high) {}

    static final Duration WARM_UP = Duration.ofDays(7);
    static final int MIN_ITEMS = 20;
    static final int MIN_WORKS = 2;
    static final double MAX_WIDTH = 2.0;
    static final int BAS_WINDOW = 30;
    static final int BAS_MIN = 10;
    static final int WORKS_WINDOW = 5;
    static final double SE_RUBRIC = 0.14;

    public BandEstimate {
        sections = List.copyOf(sections);
    }

    public static BandEstimate compute(EstimateInputs inputs, Instant now) {
        boolean warm = inputs.onboardingCompletedAt() == null
                || !now.isBefore(inputs.onboardingCompletedAt().plus(WARM_UP));
        Map<Section, SectionEstimate> sections = new EnumMap<>(Section.class);
        for (Section section : List.of(Section.READING, Section.LISTENING)) {
            sections.put(section, fromTheta(section, inputs.abilities().get(section), warm));
        }
        sections.put(Section.WRITING, writing(inputs, warm));
        sections.put(Section.SPEAKING, SectionEstimate.needMoreData(Section.SPEAKING, 0, MIN_WORKS));
        List<SectionEstimate> list = new ArrayList<>(sections.values());
        return new BandEstimate(list, overall(list), inputs.asOf());
    }

    public SectionEstimate section(Section section) {
        return sections.stream().filter(s -> s.section() == section).findFirst().orElseThrow();
    }

    private static SectionEstimate fromTheta(Section section, EstimateInputs.@Nullable Ability ability, boolean warm) {
        int n = ability == null ? 0 : ability.n();
        if (ability == null || n < MIN_ITEMS || !warm) {
            return SectionEstimate.needMoreData(section, n, MIN_ITEMS - n);
        }
        double point = BandScale.ofTheta(ability.theta());
        double lo = BandScale.ofTheta(ability.theta() - Range80.Z * ability.standardError());
        double hi = BandScale.ofTheta(ability.theta() + Range80.Z * ability.standardError());
        return ready(section, point, lo, hi, n);
    }

    /** §8.3: R_W = 10·p_bas + e_mail + e_disc of 20, scaled to the parts present; SE_p = 0.14/√n_eff. */
    private static SectionEstimate writing(EstimateInputs inputs, boolean warm) {
        List<Double> mail = head(inputs.emailScores(), WORKS_WINDOW);
        List<Double> disc = head(inputs.discussionScores(), WORKS_WINDOW);
        List<Boolean> bas = head(inputs.buildSentenceItems(), BAS_WINDOW);
        int works = mail.size() + disc.size();
        if (works < MIN_WORKS || !warm) {
            return SectionEstimate.needMoreData(Section.WRITING, works, MIN_WORKS - works);
        }
        double raw = 0;
        double max = 0;
        if (bas.size() >= BAS_MIN) {
            raw += 10 * weightedShare(bas);
            max += 10;
        }
        if (!mail.isEmpty()) {
            raw += weightedMean(mail);
            max += 5;
        }
        if (!disc.isEmpty()) {
            raw += weightedMean(disc);
            max += 5;
        }
        double p = raw / max;
        double nEff = works + bas.size() / 10.0;
        double se = SE_RUBRIC / Math.sqrt(nEff);
        if (bas.size() < BAS_MIN) {
            se *= 1.5;
        }
        return ready(
                Section.WRITING,
                BandScale.ofShare(p),
                BandScale.ofShare(p - Range80.Z * se),
                BandScale.ofShare(p + Range80.Z * se),
                works);
    }

    private static SectionEstimate ready(Section section, double point, double lo, double hi, int n) {
        Range80 range = Range80.of(lo, point, hi);
        if (range.width() > MAX_WIDTH) {
            return SectionEstimate.needMoreData(section, n, 1);
        }
        return new SectionEstimate(
                section, true, Scores.roundHalf(point), point, range.low(), range.high(), lo, hi, n, 0);
    }

    static @Nullable Overall overall(List<SectionEstimate> sections) {
        if (sections.stream().anyMatch(s -> !s.ready())) {
            return null;
        }
        double point = sections.stream()
                .mapToDouble(SectionEstimate::continuous)
                .average()
                .orElseThrow();
        double lo = sections.stream()
                .mapToDouble(SectionEstimate::continuousLow)
                .average()
                .orElseThrow();
        double hi = sections.stream()
                .mapToDouble(SectionEstimate::continuousHigh)
                .average()
                .orElseThrow();
        Range80 range = Range80.of(lo, point, hi);
        return new Overall(Scores.roundHalf(point), range.low(), range.high());
    }

    /** Weighted share of correct items, weight 0.95^age, newest first. */
    static double weightedShare(List<Boolean> items) {
        double sum = 0;
        double weights = 0;
        for (int age = 0; age < items.size(); age++) {
            double w = Math.pow(0.95, age);
            sum += items.get(age) ? w : 0;
            weights += w;
        }
        return sum / weights;
    }

    /** Weighted mean of scores, weight 0.6^age, newest first. */
    static double weightedMean(List<Double> scores) {
        double sum = 0;
        double weights = 0;
        for (int age = 0; age < scores.size(); age++) {
            double w = Math.pow(0.6, age);
            sum += scores.get(age) * w;
            weights += w;
        }
        return sum / weights;
    }

    private static <T> List<T> head(List<T> list, int n) {
        return list.size() <= n ? list : list.subList(0, n);
    }
}
