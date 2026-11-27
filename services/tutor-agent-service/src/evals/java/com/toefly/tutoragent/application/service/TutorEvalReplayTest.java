package com.toefly.tutoragent.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

/**
 * Replay of the committed tutor eval reports in CI (docs/12, phase 5c, decision 3): the prompt files must be the ones
 * the reports were made with, the metrics must equal the ones recomputed from the cases, and the thresholds of
 * docs/06 §7.3 block, since the expected labels come from the scenarios, not from a model.
 */
class TutorEvalReplayTest {

    @Test
    void replaysTutorReports() throws IOException {
        StringBuilder summary = new StringBuilder("## Tutor evals\n\n");
        List<String> failures = new ArrayList<>();
        for (String suite : List.of("tutor-chat", "weekly-review")) {
            Path file = TutorEvals.REPORTS.resolve(suite + ".json");
            assertThat(file).as("report of " + suite).exists();
            JsonNode report = TutorEvals.read(file);
            Map<String, String> hashes = TutorEvals.JSON.convertValue(report.path("promptSha256"), new TypeReference<Map<String, String>>() {});
            hashes.forEach((key, sha) -> {
                if (!TutorEvals.sha256(TutorEvals.PROMPTS.resolve(key).resolve("v1.yaml")).equals(sha)) {
                    failures.add(suite + ": prompt " + key + " changed after the report; run the live evals again");
                }
            });
            List<JsonNode> cases = new ArrayList<>();
            report.path("cases").forEach(cases::add);
            boolean chat = suite.equals("tutor-chat");
            Map<String, Double> metrics = chat ? TutorEvalMetrics.chat(cases) : TutorEvalMetrics.review(cases);
            Map<String, Double> stored = TutorEvals.JSON.convertValue(report.path("metrics"), new TypeReference<Map<String, Double>>() {});
            if (!metrics.equals(stored)) {
                failures.add(suite + ": stored metrics " + stored + " differ from recomputed " + metrics);
            }
            List<String> misses = TutorEvalMetrics.misses(metrics, chat ? TutorEvalMetrics.CHAT_THRESHOLDS : TutorEvalMetrics.REVIEW_THRESHOLDS);
            misses.forEach(m -> failures.add(suite + ": below the threshold: " + m));
            summary.append("| ").append(suite).append(" | value |\n|---|---|\n");
            metrics.forEach((k, v) -> summary.append("| ").append(k).append(" | ").append(v).append(" |\n"));
            summary.append('\n');
        }
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/evals-summary.md"), summary + (failures.isEmpty() ? "" : "**Failures:** " + failures + "\n"));
        assertThat(failures).isEmpty();
    }
}
