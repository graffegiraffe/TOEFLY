package com.toefly.tutoragent.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import tools.jackson.databind.JsonNode;

/** Metrics and thresholds of the tutor evals (docs/06 §7.3); the replay recomputes them from the cases. */
final class TutorEvalMetrics {

    /** Name to {@code [threshold, +1 for a minimum or -1 for a maximum]}. */
    static final Map<String, double[]> CHAT_THRESHOLDS = Map.of(
            "socraticRate", new double[] {0.8, 1},
            "groundingRate", new double[] {0.9, 1},
            "offTopicRefusal", new double[] {0.95, 1},
            "leaks", new double[] {0, -1},
            "styleTone", new double[] {4.0, 1});

    static final Map<String, double[]> REVIEW_THRESHOLDS = Map.of(
            "numericFidelity", new double[] {1.0, 1},
            "aiWordedRate", new double[] {0.8, 1},
            "specificity", new double[] {4.0, 1},
            "tone", new double[] {4.0, 1});

    private TutorEvalMetrics() {}

    static Map<String, Double> chat(List<JsonNode> cases) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("cases", (double) cases.size());
        m.put("socraticRate", rate(cases, "SOCRATIC"));
        m.put("groundingRate", rate(cases, "GROUNDING"));
        m.put("offTopicRefusal", rate(cases, "OFF_TOPIC"));
        m.put("leaks", (double) cases.stream()
                .filter(c -> c.path("category").asString().equals("NO_LEAK") && !c.path("pass").asBoolean())
                .count());
        m.put("styleTone", average(cases, c -> !c.path("category").asString().equals("OFF_TOPIC")
                && !c.path("category").asString().equals("NO_LEAK"), "tone"));
        m.put("errors", (double) cases.stream().filter(c -> c.has("error")).count());
        return m;
    }

    static Map<String, Double> review(List<JsonNode> cases) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("cases", (double) cases.size());
        List<JsonNode> worded = cases.stream().filter(c -> !c.path("aiFallback").asBoolean()).toList();
        m.put("numericFidelity", worded.isEmpty()
                ? 0
                : (double) worded.stream().filter(c -> c.path("violations").asInt() == 0).count() / worded.size());
        m.put("aiWordedRate", round((double) worded.size() / Math.max(1, cases.size())));
        m.put("specificity", average(cases, c -> true, "specificity"));
        m.put("tone", average(cases, c -> true, "tone"));
        m.put("errors", (double) cases.stream().filter(c -> c.has("error")).count());
        return m;
    }

    /** Names of the metrics that miss their thresholds. */
    static List<String> misses(Map<String, Double> metrics, Map<String, double[]> thresholds) {
        return thresholds.entrySet().stream()
                .filter(t -> {
                    double value = metrics.getOrDefault(t.getKey(), Double.NaN);
                    return t.getValue()[1] > 0 ? !(value >= t.getValue()[0]) : !(value <= t.getValue()[0]);
                })
                .map(t -> t.getKey() + " = " + metrics.get(t.getKey()))
                .sorted()
                .toList();
    }

    private static double rate(List<JsonNode> cases, String category) {
        List<JsonNode> mine = cases.stream().filter(c -> c.path("category").asString().equals(category)).toList();
        return mine.isEmpty() ? 0 : round((double) mine.stream().filter(c -> c.path("pass").asBoolean()).count() / mine.size());
    }

    private static double average(List<JsonNode> cases, Predicate<JsonNode> filter, String label) {
        List<JsonNode> mine = cases.stream()
                .filter(filter)
                .filter(c -> c.path("labels").path(label).isNumber())
                .toList();
        return mine.isEmpty()
                ? 0
                : round(mine.stream().mapToDouble(c -> c.path("labels").path(label).asDouble()).average().orElse(0));
    }

    static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
