package com.toefly.tutoragent.domain.chat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A change the agent proposed; it runs only after the learner confirms it (docs/06 §4.4, §4.9). */
public record PendingAction(
        UUID id,
        UUID userId,
        UUID threadId,
        UUID messageId,
        String type,
        String title,
        Map<String, Object> payload,
        Status status,
        @Nullable UUID resultId,
        Instant createdAt,
        Instant expiresAt) {

    public static final Duration LIFETIME = Duration.ofDays(2);

    public PendingAction {
        payload = Map.copyOf(payload);
    }

    public enum Status {
        PENDING,
        CONFIRMED,
        EXPIRED
    }

    public boolean expired(Instant now) {
        return status == Status.EXPIRED || (status == Status.PENDING && now.isAfter(expiresAt));
    }

    public PendingAction confirmed(UUID result) {
        return new PendingAction(
                id, userId, threadId, messageId, type, title, payload, Status.CONFIRMED, result, createdAt, expiresAt);
    }
}
