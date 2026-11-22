package com.toefly.assessment.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Live run of WritingGrader over the author's anonymised drafts and their distortions against the real ai-gateway
 * (docs/06-ai-agent.md §7, docs/12 phase 5b decision 3). Writes the report without texts to
 * {@code src/evals/reports/} and, with {@code -Devals.post=true}, sends it to ai-gateway.
 *
 * <pre>./mvnw -Pevals -pl services/assessment-service test -Dtest=WritingGraderLiveEvalTest -Devals.live=true</pre>
 */
@EnabledIfSystemProperty(named = "evals.live", matches = "true")
class WritingGraderLiveEvalTest {

    @ParameterizedTest
    @ValueSource(strings = {"writing-email", "writing-discussion"})
    @DisplayName("WritingGrader meets the thresholds of docs/06 §7.3 on the writing suites")
    void runSuite(String suite) throws Exception {
        List<EvalCase> data = EvalDataset.load(EvalSupport.root(), suite);
        checkAnchorsAreNotEvalCases(data);
        List<EvalCase> cases = new ArrayList<>();
        for (EvalCase c : data) {
            if (c.split().equals("eval")) {
                cases.add(c);
                if (c.gold() != null && c.version() == 1) {
                    cases.addAll(Distortions.of(c));
                }
            }
        }
        String runId = suite + "-" + Instant.now().toString().replace(':', '-');
        String promptKey = suite.equals("writing-email") ? "writing.grade.email" : "writing.grade.discussion";
        int promptVersion = EvalSupport.latestPromptVersion(promptKey);
        LiveGateway gateway = LiveGateway.fromEnvironment(
                        EvalSupport.root().resolve("evals/data/recordings/" + runId))
                .promptVersion(promptVersion);
        EvalRunner runner = new EvalRunner(EvalSupport.grader(gateway));
        List<CaseResult> results = runner.run(cases);
        Map<String, Double> metrics = Metrics.compute(results);
        metrics.put("errors", (double) runner.errors.size());
        EvalReport report = EvalReport.of(suite, promptKey, promptVersion, metrics, results);
        Files.createDirectories(EvalSupport.reports());
        Path file = EvalSupport.reports().resolve(suite + ".json");
        Files.writeString(file, EvalDataset.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n");
        System.out.println(Metrics.markdown(suite, metrics));
        if (Boolean.getBoolean("evals.post")) {
            gateway.postReport(report.toEvalRunReport());
        }
        assertThat(Metrics.failures(metrics)).as("metrics below thresholds for " + suite).isEmpty();
    }

    /** docs/06 §7.1: anchors and eval cases never share a text. */
    private static void checkAnchorsAreNotEvalCases(List<EvalCase> data) {
        Set<String> anchors = new HashSet<>();
        EvalSupport.KIT.anchors().values().forEach(list -> list.forEach(a -> anchors.add(EvalDataset.sha256(a.text()))));
        Map<String, String> overlap = new LinkedHashMap<>();
        data.forEach(c -> {
            if (anchors.contains(EvalDataset.sha256(c.text()))) {
                overlap.put(c.id(), c.topic());
            }
        });
        assertThat(overlap).as("eval cases that are also anchors").isEmpty();
    }
}
