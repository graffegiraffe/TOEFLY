package com.toefly.tutoragent.domain.chat;

import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A chat thread (docs/03 §11): the language overrides the profile for this thread only (docs/06 §4.1). */
public record ChatThread(
        UUID id,
        UUID userId,
        String title,
        @Nullable ChatContext context,
        ExplanationLanguage language,
        @Nullable String summary,
        @Nullable Instant summarizedUpto,
        int messageCount,
        Instant createdAt,
        @Nullable Instant lastMessageAt) {

    public static final int TITLE_LENGTH = 60;

    /** A title from the first message: its first words, at most 60 characters. */
    public static String titleOf(String text) {
        String flat = text.strip().replaceAll("\\s+", " ");
        if (flat.length() <= TITLE_LENGTH) {
            return flat;
        }
        int cut = flat.lastIndexOf(' ', TITLE_LENGTH);
        return flat.substring(0, cut > 20 ? cut : TITLE_LENGTH) + "...";
    }
}
