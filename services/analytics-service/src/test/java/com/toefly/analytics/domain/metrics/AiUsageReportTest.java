package com.toefly.analytics.domain.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiUsageReportTest {

    static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    @Test
    @DisplayName("docs/01 §7.2: AI spend by provider, model and prompt, fallback share and cost per active learner")
    void report() {
        List<AiUsage> rows = List.of(
                new AiUsage(DAY, "GROQ", "openai/gpt-oss-20b", "writing.grade", 1000, 200, 3000, 2, 0, 0),
                new AiUsage(DAY, "GROQ", "openai/gpt-oss-20b", "writing.grade", 500, 100, 1000, 1, 1, 0)
                        .plus(new AiUsage(DAY, "GROQ", "openai/gpt-oss-20b", "writing.grade", 0, 0, 0, 1, 0, 1)),
                new AiUsage(DAY.minusDays(1), "OLLAMA", "nomic-embed-text", AiUsage.NO_PROMPT, 300, 0, 0, 4, 0, 0),
                new AiUsage(DAY.minusDays(40), "GROQ", "x", "old", 1, 1, 99_999, 1, 0, 0));

        AiUsageReport report = AiUsageReport.of(DAY.minusDays(7), DAY.plusDays(1), rows, 2);

        assertThat(report.totals().calls()).isEqualTo(8);
        assertThat(report.totals().costMicroUsd()).isEqualTo(4000);
        assertThat(report.totals().fallbackShare()).isEqualTo(1.0 / 8);
        assertThat(report.totals().cacheHits()).isEqualTo(1);
        assertThat(report.costPerActiveLearnerMicroUsd()).isEqualTo(2000);
        assertThat(report.byProvider()).extracting(AiUsageReport.Breakdown::key).containsExactly("GROQ", "OLLAMA");
        assertThat(report.byModel().getFirst().key()).isEqualTo("GROQ / openai/gpt-oss-20b");
        assertThat(report.byPrompt()).extracting(AiUsageReport.Breakdown::key).containsExactly("writing.grade", "—");
        assertThat(report.byDay()).extracting(AiUsageReport.DailyCost::day).containsExactly(DAY.minusDays(1), DAY);
    }

    @Test
    @DisplayName("docs/01 §7.2: without calls or active learners the ratios are empty, not zero")
    void empty() {
        AiUsageReport report = AiUsageReport.of(DAY, DAY.plusDays(1), List.of(), 0);

        assertThat(report.totals().fallbackShare()).isNull();
        assertThat(report.costPerActiveLearnerMicroUsd()).isNull();
    }
}
