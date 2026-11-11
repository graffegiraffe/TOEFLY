package com.toefly.planning.adapter.in.web;

import com.toefly.planning.application.port.in.IdempotencyUseCase;
import com.toefly.planning.application.port.in.IdempotencyUseCase.StoredResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * POST operations with an Idempotency-Key (docs/05-api-contracts.md §2.6): the first request runs and its response
 * is stored; a repeat with the same key and body gets that response with {@code Idempotent-Replayed: true}.
 */
@Component
class IdempotentExecution {

    static final String REPLAYED = "Idempotent-Replayed";

    private final IdempotencyUseCase idempotency;
    private final JsonMapper json;

    IdempotentExecution(IdempotencyUseCase idempotency, JsonMapper json) {
        this.idempotency = idempotency;
        this.json = json;
    }

    <T> ResponseEntity<T> run(
            UUID userId,
            UUID key,
            String operation,
            @Nullable Object request,
            Class<T> type,
            Supplier<Outcome<T>> work) {
        String hash = hash(operation + "|" + (request == null ? "" : json.writeValueAsString(request)));
        Optional<StoredResponse> stored = idempotency.begin(userId, key, operation, hash);
        if (stored.isPresent()) {
            StoredResponse response = stored.get();
            ResponseEntity.BodyBuilder builder =
                    ResponseEntity.status(response.status()).header(REPLAYED, "true");
            if (response.resourceUri() != null) {
                builder.location(URI.create(response.resourceUri()));
            }
            return builder.body(json.readValue(response.body(), type));
        }
        try {
            Outcome<T> outcome = work.get();
            idempotency.complete(
                    userId, key, outcome.status().value(), json.writeValueAsString(outcome.body()), outcome.location());
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(outcome.status());
            if (outcome.location() != null) {
                builder.location(URI.create(outcome.location()));
            }
            if (outcome.etag() != null) {
                builder.eTag(outcome.etag());
            }
            return builder.body(outcome.body());
        } catch (RuntimeException failure) {
            idempotency.release(userId, key);
            throw failure;
        }
    }

    /** The result of the operation before it is stored. */
    record Outcome<T>(
            HttpStatus status,
            T body,
            @Nullable String location,
            @Nullable String etag) {
        static <T> Outcome<T> ok(T body, @Nullable String etag) {
            return new Outcome<>(HttpStatus.OK, body, null, etag);
        }

        static <T, R> Outcome<R> map(Outcome<T> outcome, Function<T, R> mapper) {
            return new Outcome<>(outcome.status(), mapper.apply(outcome.body()), outcome.location(), outcome.etag());
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
