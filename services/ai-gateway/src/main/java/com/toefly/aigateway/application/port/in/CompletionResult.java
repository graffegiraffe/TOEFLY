package com.toefly.aigateway.application.port.in;

import com.toefly.aigateway.domain.call.AiStamp;
import com.toefly.aigateway.domain.call.TokenUsage;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** {@code output} is the parsed JSON of a structured prompt, {@code text} the answer of a free-text prompt. */
public record CompletionResult(
        @Nullable Object output,
        @Nullable String text,
        AiStamp aiStamp,
        TokenUsage usage,
        long latencyMs,
        List<String> integrityFlags) {

    public CompletionResult {
        integrityFlags = List.copyOf(integrityFlags);
    }
}
