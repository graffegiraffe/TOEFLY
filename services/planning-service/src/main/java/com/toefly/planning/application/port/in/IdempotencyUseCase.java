package com.toefly.planning.application.port.in;

import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Replays responses of POST requests with the same Idempotency-Key (docs/05-api-contracts.md §2.6). */
public interface IdempotencyUseCase {

    /**
     * Claims the key for the operation; returns the stored response for a completed repeat.
     *
     * @throws IdempotencyKeyReuseException for the same key with another request
     * @throws RequestInProgressException while the first request with the key runs
     */
    Optional<StoredResponse> begin(UUID userId, UUID key, String operation, String requestHash);

    void complete(UUID userId, UUID key, int status, String body, @Nullable String resourceUri);

    void release(UUID userId, UUID key);

    record StoredResponse(int status, String body, @Nullable String resourceUri) {}
}
