package com.toefly.tutoragent.application.port.out;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * One streamed model call through the OpenAI-compatible facade of ai-gateway (docs/06 §4.6): tools are described, not
 * executed; the caller runs them and calls again.
 */
public interface ChatModelPort {

    Turn stream(TurnRequest request, Consumer<String> onToken);

    record TurnRequest(
            String useCase,
            String promptKey,
            int promptVersion,
            UUID userId,
            String conversationId,
            List<Message> messages,
            List<ToolSpec> tools) {
        public TurnRequest {
            messages = List.copyOf(messages);
            tools = List.copyOf(tools);
        }
    }

    record Message(
            String role,
            @Nullable String content,
            List<ToolCall> toolCalls,
            @Nullable String toolCallId) {
        public Message {
            toolCalls = List.copyOf(toolCalls);
        }

        public static Message system(String content) {
            return new Message("system", content, List.of(), null);
        }

        public static Message user(String content) {
            return new Message("user", content, List.of(), null);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content, List.of(), null);
        }

        public static Message tool(String callId, String content) {
            return new Message("tool", content, List.of(), callId);
        }
    }

    /** {@code parametersJson} is the JSON Schema of the arguments (docs/06 §4.5.1). */
    record ToolSpec(String name, String description, String parametersJson) {}

    record ToolCall(String id, String name, String argumentsJson) {}

    record Turn(
            String text, List<ToolCall> toolCalls, @Nullable String aiCallId, int inputTokens, int outputTokens) {
        public Turn {
            toolCalls = List.copyOf(toolCalls);
        }
    }
}
