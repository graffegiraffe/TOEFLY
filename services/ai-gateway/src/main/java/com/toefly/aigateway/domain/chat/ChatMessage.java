package com.toefly.aigateway.domain.chat;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** One message of a chat request in the OpenAI shape every provider of the gateway speaks. */
public record ChatMessage(
        Role role,
        @Nullable String content,
        List<ToolCall> toolCalls,
        @Nullable String toolCallId) {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }

    public ChatMessage {
        toolCalls = List.copyOf(toolCalls);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content, List.of(), null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content, List.of(), null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content, List.of(), null);
    }

    public ChatMessage withContent(@Nullable String content) {
        return new ChatMessage(role, content, toolCalls, toolCallId);
    }

    public ChatMessage withToolCalls(List<ToolCall> toolCalls) {
        return new ChatMessage(role, content, toolCalls, toolCallId);
    }
}
