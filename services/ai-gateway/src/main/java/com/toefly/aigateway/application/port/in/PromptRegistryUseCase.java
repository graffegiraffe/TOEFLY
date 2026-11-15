package com.toefly.aigateway.application.port.in;

import com.toefly.aigateway.domain.prompt.EvalRun;
import com.toefly.aigateway.domain.prompt.PromptParams;
import com.toefly.aigateway.domain.prompt.PromptTemplate;
import com.toefly.aigateway.domain.prompt.PromptVersion;
import com.toefly.aigateway.domain.safety.GuardPolicy;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Registry of prompts and its life cycle (§2.9). */
public interface PromptRegistryUseCase {

    List<PromptTemplate> templates();

    List<VersionView> versions(String key);

    PromptVersion active(String key);

    PromptVersion createVersion(String key, NewVersion version, String actor);

    PromptVersion activate(String key, int version, String actor);

    /** Adds versions of the prompt files that the registry does not have yet, as drafts. */
    int syncSources();

    record NewVersion(
            String description,
            String system,
            String user,
            @Nullable String outputSchema,
            PromptParams params,
            @Nullable Integer maxInputTokens,
            Set<String> untrusted,
            @Nullable GuardPolicy guardPolicy,
            @Nullable String evalSuite) {}

    record VersionView(PromptVersion version, Optional<EvalRun> latestEval, boolean active) {}
}
