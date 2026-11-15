package com.toefly.aigateway.application.port.in;

import com.toefly.aigateway.domain.prompt.EvalRun;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Reports of eval runs from CI (§7.4); a passed run moves a draft to CANDIDATE. */
public interface EvalRunUseCase {

    EvalRun record(EvalReport report);

    List<EvalRun> page(@Nullable String promptKey, int limit);

    record EvalReport(
            String promptKey,
            int promptVersion,
            String suite,
            String mode,
            boolean passed,
            Map<String, Double> metrics,
            @Nullable String reportUrl,
            Instant ranAt) {}
}
