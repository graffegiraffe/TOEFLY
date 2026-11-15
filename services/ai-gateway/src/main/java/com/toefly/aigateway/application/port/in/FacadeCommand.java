package com.toefly.aigateway.application.port.in;

import com.toefly.aigateway.domain.chat.ChatMessage;
import com.toefly.aigateway.domain.chat.ToolSpec;
import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.routing.AiUseCase;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A turn of an agent loop through the OpenAI-compatible facade (§2.2.2); metadata from the X-Toefly headers. */
public record FacadeCommand(
        String callerService,
        AiUseCase useCase,
        @Nullable String promptKey,
        @Nullable Integer promptVersion,
        @Nullable UUID userId,
        @Nullable String conversationId,
        List<String> piiHints,
        Priority priority,
        @Nullable String feature,
        List<ChatMessage> messages,
        List<ToolSpec> tools,
        @Nullable String toolChoice,
        @Nullable Double temperature,
        @Nullable Integer maxTokens,
        @Nullable UUID correlationId) {

    public FacadeCommand {
        piiHints = List.copyOf(piiHints);
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
    }
}
