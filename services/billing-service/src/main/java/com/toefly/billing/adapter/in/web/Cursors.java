package com.toefly.billing.adapter.in.web;

import com.toefly.billing.application.port.in.BillingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Opaque keyset cursor {@code base64url(epochMillis:id)}. */
final class Cursors {

    private Cursors() {}

    record Position(Instant at, UUID id) {}

    static String encode(Instant at, UUID id) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((at.toEpochMilli() + ":" + id).getBytes(StandardCharsets.UTF_8));
    }

    static @Nullable Position decode(@Nullable String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":", 2);
            return new Position(Instant.ofEpochMilli(Long.parseLong(parts[0])), UUID.fromString(parts[1]));
        } catch (RuntimeException malformed) {
            throw BillingException.invalid("The cursor is invalid.");
        }
    }
}
