package com.toefly.profile.adapter.in.web;

import org.jspecify.annotations.Nullable;

/** Strong ETags carry the aggregate version: {@code "7"} (docs/05-api-contracts.md §2.7). */
final class ETags {

    private ETags() {}

    static String of(long version) {
        return "\"" + version + "\"";
    }

    static long requireVersion(@Nullable String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException();
        }
        String value = ifMatch.strip();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new PreconditionRequiredException();
        }
    }
}
