package com.toefly.planning.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Responses of requests with an Idempotency-Key, kept 24 hours (docs/05-api-contracts.md §2.6). */
public interface IdempotencyStorePort {

    /** Claims the key; false when a record for the key exists already. */
    boolean claim(UUID userId, String key, String operation, String requestHash, Instant expiresAt);

    Optional<Record> find(UUID userId, String key);

    void complete(UUID userId, String key, int status, String body, @Nullable String resourceUri);

    void release(UUID userId, String key);

    record Record(
            String operation,
            String requestHash,
            boolean completed,
            @Nullable Integer status,
            @Nullable String body,
            @Nullable String resourceUri) {}
}
