package com.toefly.assessment.evals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code eval-run.json} without texts (docs/06-ai-agent.md §7.4): versions and sha256 of the prompt and anchors,
 * metrics with thresholds and per-case results.
 */
public record EvalReport(
        String suite,
        String promptKey,
        int promptVersion,
        String promptSha256,
        String anchorsSha256,
        String mode,
        Instant ranAt,
        Map<String, Double> metrics,
        Map<String, double[]> thresholds,
        boolean passed,
        List<CaseResult> cases) {

    static EvalReport of(String suite, String promptKey, int version, Map<String, Double> metrics, List<CaseResult> cases) {
        return new EvalReport(
                suite,
                promptKey,
                version,
                currentPromptSha256(promptKey, version),
                currentAnchorsSha256(),
                "FULL",
                Instant.now(),
                metrics,
                Metrics.THRESHOLDS,
                Metrics.passed(metrics),
                cases);
    }

    static String currentPromptSha256(String key, int version) {
        try {
            return EvalDataset.sha256(Files.readAllBytes(EvalSupport.prompt(key, version)));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Hash of the anchor file itself; a walk over the anchor map depended on its unordered iteration. */
    static String currentAnchorsSha256() {
        try {
            return EvalDataset.sha256(Files.readAllBytes(
                    EvalSupport.module().resolve("src/main/resources/grading/anchors.json")));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Body of {@code POST /api/v1/admin/ai/eval-runs}. */
    Map<String, Object> toEvalRunReport() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("promptKey", promptKey);
        body.put("promptVersion", promptVersion);
        body.put("suite", suite);
        body.put("mode", mode);
        body.put("passed", passed);
        body.put("metrics", metrics);
        body.put("ranAt", ranAt.toString());
        Map<String, Map<String, Double>> bounds = new LinkedHashMap<>();
        thresholds.forEach((name, t) -> {
            Map<String, Double> bound = new LinkedHashMap<>();
            if (!Double.isNaN(t[0])) {
                bound.put("min", t[0]);
            }
            if (!Double.isNaN(t[1])) {
                bound.put("max", t[1]);
            }
            bounds.put(name, bound);
        });
        body.put("thresholds", bounds);
        body.put("cases", cases.stream()
                .map(c -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("caseId", c.caseId());
                    row.put("split", c.split());
                    row.put("gold", c.gold());
                    row.put("predicted", c.predicted());
                    row.put("passes", c.passes());
                    row.put("flags", c.flags());
                    return row;
                })
                .toList());
        return body;
    }
}
