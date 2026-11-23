package com.toefly.tutoragent.domain.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One message of a thread; the assistant's answer is stored while it streams (docs/06 §4.9). */
public record ChatMessage(
        UUID id,
        UUID threadId,
        UUID userId,
        Role role,
        String text,
        Status status,
        @Nullable TutorIntent intent,
        @Nullable ChatContext context,
        List<String> tools,
        @Nullable String aiCallId,
        @Nullable Integer tokensIn,
        @Nullable Integer tokensOut,
        Instant createdAt) {

    public ChatMessage {
        tools = List.copyOf(tools);
    }

    public enum Role {
        USER,
        ASSISTANT
    }

    public enum Status {
        COMPLETE,
        STREAMING,
        INTERRUPTED,
        RETRACTED,
        FAILED
    }
}
