package com.toefly.tutoragent.domain.chat;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A reference to the screen, never its text: the service loads the data from the owners (docs/06 §4.7). */
public record ChatContext(
        ChatContextType type,
        @Nullable UUID ref,
        @Nullable UUID itemId,
        @Nullable String label) {

    public static ChatContext general() {
        return new ChatContext(ChatContextType.GENERAL, null, null, null);
    }

    public ChatContext withType(ChatContextType newType) {
        return new ChatContext(newType, ref, itemId, label);
    }

    public ChatContext withLabel(@Nullable String newLabel) {
        return new ChatContext(type, ref, itemId, newLabel);
    }
}
