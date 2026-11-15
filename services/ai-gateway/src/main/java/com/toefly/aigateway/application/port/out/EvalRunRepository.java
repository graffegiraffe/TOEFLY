package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.prompt.EvalRun;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Reports of eval runs (§7.4). */
public interface EvalRunRepository {

    void save(EvalRun run);

    List<EvalRun> page(@Nullable String promptKey, int limit);

    Optional<EvalRun> latestPassed(String promptKey, int version);
}
