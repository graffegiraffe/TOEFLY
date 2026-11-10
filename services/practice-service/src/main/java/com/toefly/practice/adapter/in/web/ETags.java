package com.toefly.practice.adapter.in.web;

import org.jspecify.annotations.Nullable;

/** Strong ETags carry the stream version: {@code "7"} (docs/05-api-contracts.md §2.7). */
final class ETags {

    private ETags() {}

    static String of(long version) {
        return "\"" + version + "\"";
    }

    static @Nullable Integer version(@Nullable String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String value = header.strip();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        try {
            return Integer.parseInt(value.replace("\"", ""));
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    static boolean matches(@Nullable String ifNoneMatch, long version) {
        Integer requested = version(ifNoneMatch);
        return requested != null && requested == version;
    }
}
