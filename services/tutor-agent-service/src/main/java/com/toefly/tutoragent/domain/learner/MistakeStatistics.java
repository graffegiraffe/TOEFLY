package com.toefly.tutoragent.domain.learner;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Weights, error rates, trends, statuses and the ranking of subcategories (docs/06 §6.2): weight decays with a half-life
 * of 21 days; the error rate counts 28 days; the trend compares the last 14 days with the 14 before.
 */
public final class MistakeStatistics {

    static final double HALF_LIFE_DAYS = 21.0;
    static final double RESOLVED_WEIGHT = 0.75;
    static final double ACTIVE_WEIGHT = 1.5;
    static final double ACTIVE_ERROR_RATE = 0.2;

    private MistakeStatistics() {}

    /** Statistics of every subcategory with at least one record, by priority, highest first. */
    public static List<MistakeStat> compute(
            List<MistakeRecord> records, List<Opportunity> opportunities, Taxonomy taxonomy, Instant now) {
        Map<String, List<MistakeRecord>> bySubcategory = records.stream()
                .collect(Collectors.groupingBy(MistakeRecord::subcategory, TreeMap::new, Collectors.toList()));
        List<MistakeStat> stats = new ArrayList<>();
        bySubcategory.forEach((code, own) -> stats.add(stat(code, own, opportunities, taxonomy, now)));
        stats.sort(
                Comparator.comparingDouble(MistakeStat::priority).reversed().thenComparing(MistakeStat::subcategory));
        return List.copyOf(stats);
    }

    static double decay(Instant at, Instant now) {
        double days = Math.max(0, Duration.between(at, now).toMinutes()) / (24.0 * 60);
        return Math.exp(-Math.log(2) * days / HALF_LIFE_DAYS);
    }

    private static MistakeStat stat(
            String code, List<MistakeRecord> own, List<Opportunity> opportunities, Taxonomy taxonomy, Instant now) {
        MistakeCategory category = MistakeCategory.ofSubcategory(code);
        double weight = own.stream()
                .mapToDouble(r -> r.severity().weight() * decay(r.createdAt(), now))
                .sum();
        List<Opportunity> mine =
                opportunities.stream().filter(o -> o.covers(code)).toList();
        int occurrences28 = count(own, now.minus(Duration.ofDays(28)), now);
        int opportunities28 = countOpportunities(mine, now.minus(Duration.ofDays(28)), now);
        double errorRate = rate(occurrences28, opportunities28);
        MistakeTrend trend = trend(own, mine, now);
        MistakeStatus status = status(weight, errorRate, own, mine);
        Instant lastSeen = own.stream()
                .map(MistakeRecord::createdAt)
                .max(Comparator.naturalOrder())
                .orElse(now);
        return new MistakeStat(
                code,
                category,
                round(weight),
                round(errorRate),
                trend,
                status,
                round(weight * category.impact() * status.rankingFactor()),
                occurrences28,
                count(own, now.minus(Duration.ofDays(7)), now),
                opportunities28,
                lastSeen,
                taxonomy.unitRefs(code));
    }

    private static MistakeTrend trend(List<MistakeRecord> own, List<Opportunity> mine, Instant now) {
        Instant mid = now.minus(Duration.ofDays(14));
        Instant start = now.minus(Duration.ofDays(28));
        double recent = rate(count(own, mid, now), countOpportunities(mine, mid, now));
        double before = rate(count(own, start, mid), countOpportunities(mine, start, mid));
        if (before == 0) {
            return recent == 0 ? MistakeTrend.STABLE : MistakeTrend.WORSENING;
        }
        if (recent < 0.6 * before) {
            return MistakeTrend.IMPROVING;
        }
        return recent > 1.4 * before ? MistakeTrend.WORSENING : MistakeTrend.STABLE;
    }

    private static MistakeStatus status(
            double weight, double errorRate, List<MistakeRecord> own, List<Opportunity> mine) {
        if (weight < RESOLVED_WEIGHT && lastThreeClean(own, mine)) {
            return MistakeStatus.RESOLVED;
        }
        return weight >= ACTIVE_WEIGHT && errorRate >= ACTIVE_ERROR_RATE ? MistakeStatus.ACTIVE : MistakeStatus.WATCH;
    }

    /** The three latest opportunities of the subcategory had no mistake of it. */
    private static boolean lastThreeClean(List<MistakeRecord> own, List<Opportunity> mine) {
        List<Opportunity> latest = mine.stream()
                .sorted(Comparator.comparing(Opportunity::occurredAt).reversed())
                .limit(3)
                .toList();
        if (latest.size() < 3) {
            return false;
        }
        return latest.stream()
                .noneMatch(o -> own.stream()
                        .anyMatch(r -> r.sourceId().equals(o.sourceId()) && Objects.equals(r.itemId(), o.itemId())));
    }

    private static int count(List<MistakeRecord> own, Instant from, Instant to) {
        return (int) own.stream()
                .filter(r -> !r.createdAt().isBefore(from) && r.createdAt().isBefore(to.plusMillis(1)))
                .count();
    }

    private static int countOpportunities(List<Opportunity> mine, Instant from, Instant to) {
        return (int) mine.stream()
                .filter(o -> !o.occurredAt().isBefore(from) && o.occurredAt().isBefore(to.plusMillis(1)))
                .count();
    }

    private static double rate(int occurrences, int opportunities) {
        return (double) occurrences / Math.max(1, opportunities);
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
