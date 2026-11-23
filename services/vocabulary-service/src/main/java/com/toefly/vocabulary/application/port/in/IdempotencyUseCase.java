package com.toefly.vocabulary.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Replay of responses by Idempotency-Key for 24 h (docs/05 §2.6). */
public interface IdempotencyUseCase {

    Optional<StoredResponse> begin(UUID userId, String key, String operation, String requestHash);

    void complete(UUID userId, String key, int status, String body);

    void release(UUID userId, String key);

    record StoredResponse(int status, String body) {}
}
