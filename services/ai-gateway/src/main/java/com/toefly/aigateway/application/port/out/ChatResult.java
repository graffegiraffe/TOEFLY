package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.call.TokenUsage;
import com.toefly.aigateway.domain.chat.ToolCall;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Answer of a provider. {@code reportedLimits} are the limits the provider announced in its headers (for example
 * {@code TPM → 6000}); empty when it announced none.
 */
public record ChatResult(
        @Nullable String content,
        List<ToolCall> toolCalls,
        @Nullable String finishReason,
        TokenUsage usage,
        Map<String, Long> reportedLimits) {

    public ChatResult {
        toolCalls = List.copyOf(toolCalls);
        reportedLimits = Map.copyOf(reportedLimits);
    }
}
