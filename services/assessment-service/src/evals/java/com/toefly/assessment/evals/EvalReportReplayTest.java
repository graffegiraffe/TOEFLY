package com.toefly.assessment.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Replay mode of {@code ai-evals.yml} (docs/12 phase 5b decision 3): the committed report must match the prompt and
 * anchors in the repository and its metrics must equal the ones recomputed from the per-case results. While the gold
 * scores come from an AI rater, metrics below the thresholds are listed as warnings in the summary instead of failing.
 */
class EvalReportReplayTest {

    @TestFactory
    @DisplayName("committed eval reports of WritingGrader are current and consistent")
    Stream<DynamicTest> reports() throws IOException {
        List<Path> files;
        try (Stream<Path> listing = Files.list(EvalSupport.reports())) {
            files = listing.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertThat(files).as("eval reports in src/evals/reports").isNotEmpty();
        StringBuilder summary = new StringBuilder("## Evals of WritingGrader (replay)\n\n");
        Stream<DynamicTest> tests = files.stream().map(file -> DynamicTest.dynamicTest(file.getFileName().toString(), () -> {
            EvalReport report = EvalDataset.JSON.readValue(Files.readString(file), EvalReport.class);
            assertThat(report.promptSha256())
                    .as("%s v%d changed after the last eval run: run the evals again", report.promptKey(), report.promptVersion())
                    .isEqualTo(EvalReport.currentPromptSha256(report.promptKey(), report.promptVersion()));
            assertThat(report.anchorsSha256()).as("anchors changed after the last eval run").isEqualTo(EvalReport.currentAnchorsSha256());
            var recomputed = Metrics.compute(report.cases());
            assertThat(Metrics.same(recomputed, report.metrics())).as("metrics match the per-case results").isTrue();
            summary.append(Metrics.markdown(report.suite(), recomputed)).append('\n');
            var below = Metrics.failures(recomputed);
            if (!below.isEmpty()) {
                summary.append("> **Warning:** below the thresholds of docs/06 §7.3: ")
                        .append(String.join(", ", below))
                        .append(". The gold scores come from an AI rater (docs/12 phase 5b decisions 2 and 3).\n\n");
            }
            Files.createDirectories(EvalSupport.module().resolve("target"));
            Files.writeString(EvalSupport.module().resolve("target/evals-summary.md"), summary.toString());
        }));
        return tests;
    }
}
