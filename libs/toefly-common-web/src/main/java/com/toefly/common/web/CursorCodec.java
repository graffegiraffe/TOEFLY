package com.toefly.common.web;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Opaque base64url cursors for keyset pagination. A cursor carries the sort key values of the last item and a
 * fingerprint of the sort and filters; a cursor used with other filters is rejected as {@code invalid-cursor}.
 */
public final class CursorCodec {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private CursorCodec() {}

    public static String encode(String fingerprint, List<String> keyValues) {
        StringBuilder plain = new StringBuilder(part(fingerprint));
        keyValues.forEach(value -> plain.append('.').append(part(value)));
        return ENCODER.encodeToString(plain.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static List<String> decode(String cursor, String fingerprint) {
        try {
            String plain = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
            List<String> parts = Arrays.stream(plain.split("\\.", -1))
                    .map(part -> new String(DECODER.decode(part), StandardCharsets.UTF_8))
                    .toList();
            if (parts.isEmpty() || !parts.getFirst().equals(fingerprint)) {
                throw new InvalidCursorException();
            }
            return parts.subList(1, parts.size());
        } catch (IllegalArgumentException malformed) {
            throw new InvalidCursorException();
        }
    }

    /** Validates the {@code limit} query parameter: 1–100, 20 when absent. */
    public static int limit(@Nullable Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        if (requested < 1 || requested > MAX_LIMIT) {
            throw new InvalidLimitException(requested);
        }
        return requested;
    }

    private static String part(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
