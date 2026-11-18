package com.toefly.aigateway.domain.prompt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Report of one eval run of a prompt version (§7.4). */
public record EvalRun(
        UUID id,
        String promptKey,
        int promptVersion,
        String suite,
        String mode,
        boolean passed,
        Map<String, Double> metrics,
        @Nullable String reportUrl,
        Instant ranAt,
        Instant receivedAt,
        Map<String, Bound> thresholds,
        List<CaseResult> cases) {

    /** Threshold of a metric: at least {@code min}, at most {@code max}. */
    public record Bound(@Nullable Double min, @Nullable Double max) {}

    /** Result of one case of the suite, without its text (docs/05 §6.15). */
    public record CaseResult(
            String caseId,
            String split,
            @Nullable Double gold,
            double predicted,
            List<Integer> passes,
            List<String> flags) {

        public CaseResult {
            passes = List.copyOf(passes);
            flags = List.copyOf(flags);
        }
    }

    public EvalRun {
        metrics = Map.copyOf(metrics);
        thresholds = Map.copyOf(thresholds);
        cases = List.copyOf(cases);
    }
}
