package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.chat.ChatMessage;
import com.toefly.aigateway.domain.chat.ToolSpec;
import com.toefly.aigateway.domain.routing.ModelRef;
import com.toefly.aigateway.domain.routing.RouteParams;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** What a provider adapter sends: messages already masked, a JSON Schema for structured output, tools if any. */
public record ChatRequest(
        ModelRef model,
        List<ChatMessage> messages,
        RouteParams params,
        @Nullable String jsonSchema,
        List<ToolSpec> tools,
        @Nullable String toolChoice) {

    public ChatRequest {
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
    }

    public ChatRequest withModel(ModelRef model) {
        return new ChatRequest(model, messages, params, jsonSchema, tools, toolChoice);
    }

    public ChatRequest withMessages(List<ChatMessage> messages) {
        return new ChatRequest(model, messages, params, jsonSchema, tools, toolChoice);
    }
}
