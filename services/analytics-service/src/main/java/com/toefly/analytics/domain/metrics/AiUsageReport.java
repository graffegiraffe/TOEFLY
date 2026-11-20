package com.toefly.analytics.domain.metrics;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** AI spend of a period for the admin (`/admin/ai-spend`): totals, breakdowns and the cost per active learner. */
public record AiUsageReport(
        LocalDate from,
        LocalDate to,
        Totals totals,
        List<Breakdown> byProvider,
        List<Breakdown> byModel,
        List<Breakdown> byPrompt,
        List<DailyCost> byDay,
        int activeLearners,
        @Nullable Long costPerActiveLearnerMicroUsd) {

    public AiUsageReport {
        byProvider = List.copyOf(byProvider);
        byModel = List.copyOf(byModel);
        byPrompt = List.copyOf(byPrompt);
        byDay = List.copyOf(byDay);
    }

    public record Totals(
            int calls, long tokensIn, long tokensOut, long costMicroUsd, int fallbackCalls, int cacheHits) {

        /** Share of calls served by a fallback provider of the chain. */
        public @Nullable Double fallbackShare() {
            return calls == 0 ? null : (double) fallbackCalls / calls;
        }
    }

    public record Breakdown(String key, Totals totals) {}

    public record DailyCost(LocalDate day, int calls, long costMicroUsd) {}

    /**
     * @param activeLearners learners with any active time in the period; the whole spend is divided by them (docs/01 §7.2)
     */
    public static AiUsageReport of(LocalDate from, LocalDate to, Collection<AiUsage> rows, int activeLearners) {
        List<AiUsage> inRange = rows.stream()
                .filter(r -> !r.day().isBefore(from) && r.day().isBefore(to))
                .toList();
        Totals totals = totals(inRange);
        Map<LocalDate, List<AiUsage>> days = group(inRange, AiUsage::day);
        List<DailyCost> byDay = days.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    Totals t = totals(e.getValue());
                    return new DailyCost(e.getKey(), t.calls(), t.costMicroUsd());
                })
                .toList();
        return new AiUsageReport(
                from,
                to,
                totals,
                breakdown(inRange, AiUsage::provider),
                breakdown(inRange, r -> r.provider() + " / " + r.model()),
                breakdown(inRange, r -> r.promptKey().isEmpty() ? "—" : r.promptKey()),
                byDay,
                activeLearners,
                activeLearners == 0 ? null : totals.costMicroUsd() / activeLearners);
    }

    private static List<Breakdown> breakdown(List<AiUsage> rows, Function<AiUsage, String> key) {
        return group(rows, key).entrySet().stream()
                .map(e -> new Breakdown(e.getKey(), totals(e.getValue())))
                .sorted(Comparator.comparingLong((Breakdown b) -> b.totals().costMicroUsd())
                        .reversed()
                        .thenComparing(b -> -b.totals().calls())
                        .thenComparing(Breakdown::key))
                .toList();
    }

    private static <K> Map<K, List<AiUsage>> group(List<AiUsage> rows, Function<AiUsage, K> key) {
        Map<K, List<AiUsage>> groups = new LinkedHashMap<>();
        for (AiUsage row : rows) {
            groups.computeIfAbsent(key.apply(row), k -> new java.util.ArrayList<>())
                    .add(row);
        }
        return groups;
    }

    private static Totals totals(List<AiUsage> rows) {
        int calls = 0;
        long in = 0;
        long out = 0;
        long cost = 0;
        int fallback = 0;
        int cache = 0;
        for (AiUsage row : rows) {
            calls += row.calls();
            in += row.tokensIn();
            out += row.tokensOut();
            cost += row.costMicroUsd();
            fallback += row.fallbackCalls();
            cache += row.cacheHits();
        }
        return new Totals(calls, in, out, cost, fallback, cache);
    }
}
