package com.toefly.vocabulary.adapter.in.web;

import com.toefly.vocabulary.application.port.in.IdempotencyUseCase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Replays a stored response for a repeated {@code Idempotency-Key} (docs/05 §2.6). */
@Component
public class Idempotent {

    private final IdempotencyUseCase idempotency;
    private final JsonMapper json;

    public Idempotent(IdempotencyUseCase idempotency, JsonMapper json) {
        this.idempotency = idempotency;
        this.json = json;
    }

    /** Runs {@code action} once per key; its status is chosen by the action (201 or 200). */
    public <T> ResponseEntity<T> run(
            UUID userId,
            String key,
            String operation,
            @Nullable Object request,
            Class<T> type,
            Supplier<ResponseEntity<T>> action) {
        String hash = hash(operation + ":" + (request == null ? "" : json.writeValueAsString(request)));
        Optional<IdempotencyUseCase.StoredResponse> stored = idempotency.begin(userId, key, operation, hash);
        if (stored.isPresent()) {
            return ResponseEntity.status(stored.get().status())
                    .header("Idempotent-Replayed", "true")
                    .body(json.readValue(stored.get().body(), type));
        }
        ResponseEntity<T> response;
        try {
            response = action.get();
        } catch (RuntimeException failure) {
            idempotency.release(userId, key);
            throw failure;
        }
        idempotency.complete(
                userId, key, response.getStatusCode().value(), json.writeValueAsString(response.getBody()));
        return response;
    }

    public <T> ResponseEntity<T> run(
            UUID userId,
            String key,
            String operation,
            @Nullable Object request,
            Class<T> type,
            HttpStatus status,
            Supplier<T> action) {
        return run(
                userId,
                key,
                operation,
                request,
                type,
                () -> ResponseEntity.status(status).body(action.get()));
    }

    private static String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
