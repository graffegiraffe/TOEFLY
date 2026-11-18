package com.toefly.assessment.evals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Metrics of docs/06-ai-agent.md §7.3 for the writing suites and their thresholds. */
public final class Metrics {

    /** Metric → {min, max}; null bound means none. Precision with an LLM judge is not computed (decision 2). */
    public static final Map<String, double[]> THRESHOLDS = new LinkedHashMap<>();

    static {
        THRESHOLDS.put("within05", new double[] {0.80, Double.NaN});
        THRESHOLDS.put("within10", new double[] {0.95, Double.NaN});
        THRESHOLDS.put("qwk", new double[] {0.70, Double.NaN});
        THRESHOLDS.put("absBias", new double[] {Double.NaN, 0.30});
        THRESHOLDS.put("monotonicity", new double[] {0.95, Double.NaN});
        THRESHOLDS.put("tutorErrorRecall", new double[] {0.70, Double.NaN});
        THRESHOLDS.put("anchoring", new double[] {0.97, Double.NaN});
    }

    private Metrics() {}

    public static Map<String, Double> compute(List<CaseResult> results) {
        List<CaseResult> gold = results.stream().filter(r -> r.gold() != null).toList();
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("cases", (double) results.size());
        m.put("goldCases", (double) gold.size());
        m.put("within05", share(gold, r -> Math.abs(r.predicted() - r.gold()) <= 0.5));
        m.put("within10", share(gold, r -> Math.abs(r.predicted() - r.gold()) <= 1.0));
        m.put("mae", gold.stream().mapToDouble(r -> Math.abs(r.predicted() - r.gold())).average().orElse(0));
        double bias = gold.stream().mapToDouble(r -> r.predicted() - r.gold()).average().orElse(0);
        m.put("bias", bias);
        m.put("absBias", Math.abs(bias));
        m.put("qwk", qwk(gold.stream().map(r -> round(r.gold())).toList(), gold.stream().map(r -> round(r.predicted())).toList()));
        Map<String, CaseResult> byId = results.stream().collect(Collectors.toMap(CaseResult::caseId, Function.identity()));
        List<CaseResult> distorted = results.stream().filter(r -> r.distortionOf() != null).toList();
        m.put("monotonicity", share(distorted, r -> {
            CaseResult original = byId.get(r.distortionOf());
            return original == null || r.predicted() <= original.predicted();
        }));
        int errors = results.stream().mapToInt(CaseResult::tutorErrors).sum();
        int found = results.stream().mapToInt(CaseResult::tutorErrorsFound).sum();
        m.put("tutorErrorRecall", errors == 0 ? 1.0 : (double) found / errors);
        int comments = results.stream().mapToInt(CaseResult::comments).sum();
        int anchored = results.stream().mapToInt(CaseResult::commentsAnchored).sum();
        m.put("anchoring", comments == 0 ? 1.0 : (double) anchored / comments);
        return m;
    }

    public static boolean passed(Map<String, Double> metrics) {
        return failures(metrics).isEmpty();
    }

    /** Agreement on fewer gold cases means nothing; the suite must be scored first. */
    static final int MIN_GOLD_CASES = 10;

    public static List<String> failures(Map<String, Double> metrics) {
        List<String> failed = new java.util.ArrayList<>();
        if (metrics.getOrDefault("goldCases", 0.0) < MIN_GOLD_CASES) {
            failed.add("goldCases");
        }
        THRESHOLDS.entrySet().stream()
                .filter(t -> {
                    Double value = metrics.get(t.getKey());
                    double min = t.getValue()[0];
                    double max = t.getValue()[1];
                    return value == null
                            || (!Double.isNaN(min) && value < min)
                            || (!Double.isNaN(max) && value > max);
                })
                .map(Map.Entry::getKey)
                .forEach(failed::add);
        return failed;
    }

    static int round(double score) {
        return (int) Math.floor(score + 0.5);
    }

    /** Quadratic weighted kappa on integer scores 0–5. */
    static double qwk(List<Integer> a, List<Integer> b) {
        int k = 6;
        int n = a.size();
        if (n == 0) {
            return 0;
        }
        double[][] observed = new double[k][k];
        double[] histA = new double[k];
        double[] histB = new double[k];
        for (int i = 0; i < n; i++) {
            observed[a.get(i)][b.get(i)]++;
            histA[a.get(i)]++;
            histB[b.get(i)]++;
        }
        double num = 0;
        double den = 0;
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                double w = Math.pow(i - j, 2) / Math.pow(k - 1, 2);
                num += w * observed[i][j];
                den += w * histA[i] * histB[j] / n;
            }
        }
        return den == 0 ? 1.0 : 1 - num / den;
    }

    private static double share(List<CaseResult> results, java.util.function.Predicate<CaseResult> ok) {
        if (results.isEmpty()) {
            return 1.0;
        }
        return (double) results.stream().filter(ok).count() / results.size();
    }

    static String markdown(String suite, Map<String, Double> metrics) {
        StringBuilder out = new StringBuilder("| Metric (" + suite + ") | Value | Threshold |\n|---|---|---|\n");
        metrics.forEach((name, value) -> {
            double[] t = THRESHOLDS.get(name);
            String bound = t == null ? "" : !Double.isNaN(t[0]) ? "≥ " + t[0] : "≤ " + t[1];
            out.append("| ").append(name).append(" | ").append(String.format(java.util.Locale.ROOT, "%.3f", value))
                    .append(" | ").append(bound).append(" |\n");
        });
        return out.toString();
    }

    static boolean same(Map<String, Double> a, Map<String, Double> b) {
        return a.keySet().stream().allMatch(key -> b.containsKey(key)
                && Math.abs(Objects.requireNonNull(a.get(key)) - Objects.requireNonNull(b.get(key))) < 1e-9);
    }
}
