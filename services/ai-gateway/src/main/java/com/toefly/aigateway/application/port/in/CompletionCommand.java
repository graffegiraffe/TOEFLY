package com.toefly.aigateway.application.port.in;

import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.routing.AiUseCase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A templated call of a worker (§2.2.1). */
public record CompletionCommand(
        String callerService,
        AiUseCase useCase,
        String promptKey,
        @Nullable Integer promptVersion,
        Map<String, Object> variables,
        @Nullable UUID userId,
        List<String> piiHints,
        List<String> allowList,
        @Nullable String feature,
        Priority priority,
        @Nullable Long maxWaitMs,
        CachePolicy cachePolicy,
        @Nullable String modelOverride,
        @Nullable String passTag,
        @Nullable UUID correlationId) {

    public CompletionCommand {
        variables = Map.copyOf(variables);
        piiHints = List.copyOf(piiHints);
        allowList = List.copyOf(allowList);
    }
}
