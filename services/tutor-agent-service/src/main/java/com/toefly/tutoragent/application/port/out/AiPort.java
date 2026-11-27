package com.toefly.tutoragent.application.port.out;

import com.toefly.tutoragent.application.port.AiUnavailableException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Prompt-registry completions and embeddings of ai-gateway (docs/05 §6.15). */
public interface AiPort {

    /** A structured or free completion; {@link AiUnavailableException} when no model answered. */
    Completion complete(CompletionCall call);

    /** Vectors of nomic-embed-text, one per text. */
    List<float[]> embed(List<String> texts, @Nullable UUID userId);

    record CompletionCall(
            String useCase,
            String promptKey,
            @Nullable Integer promptVersion,
            Map<String, Object> variables,
            @Nullable UUID userId,
            String feature,
            String priority,
            long maxWaitMs,
            @Nullable String idempotencyKey,
            @Nullable String modelOverride) {
        public CompletionCall {
            variables = Map.copyOf(variables);
        }
    }

    /** {@code output} is the JSON object of a structured prompt as maps and lists; {@code text} of a free one. */
    record Completion(
            @Nullable Map<String, Object> output,
            @Nullable String text,
            AiStamp stamp,
            int inputTokens,
            int outputTokens) {}

    record AiStamp(
            String aiCallId,
            @Nullable String promptKey,
            @Nullable Integer promptVersion,
            String provider,
            String model,
            boolean degraded) {}
}
