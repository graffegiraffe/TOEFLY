package com.toefly.aigateway.domain.prompt;

import com.toefly.aigateway.domain.routing.AiUseCase;
import com.toefly.aigateway.domain.safety.GuardPolicy;
import java.time.Instant;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Immutable version of a prompt (§2.9). Variables named in {@code untrusted} are the learner's text: they are
 * masked, checked by the guard of {@code guardPolicy} and wrapped in nonce tags before rendering (§3.1, §3.5).
 */
public record PromptVersion(
        String key,
        int version,
        AiUseCase useCase,
        String description,
        String system,
        String user,
        @Nullable String outputSchema,
        PromptParams params,
        int maxInputTokens,
        Set<String> untrusted,
        GuardPolicy guardPolicy,
        @Nullable String evalSuite,
        PromptStatus status,
        String sourceHash,
        String createdBy,
        Instant createdAt) {

    public static final int DEFAULT_MAX_INPUT_TOKENS = 6000;

    public PromptVersion {
        if (version < 1) {
            throw new PromptRuleException("validation-failed", "version starts at 1");
        }
        if (maxInputTokens <= 0) {
            throw new PromptRuleException("validation-failed", "maxInputTokens must be positive");
        }
        untrusted = Set.copyOf(untrusted);
    }

    public String id() {
        return key + "@" + version;
    }

    public boolean structured() {
        return outputSchema != null;
    }

    public PromptVersion withStatus(PromptStatus status) {
        return new PromptVersion(
                key,
                version,
                useCase,
                description,
                system,
                user,
                outputSchema,
                params,
                maxInputTokens,
                untrusted,
                guardPolicy,
                evalSuite,
                status,
                sourceHash,
                createdBy,
                createdAt);
    }
}
