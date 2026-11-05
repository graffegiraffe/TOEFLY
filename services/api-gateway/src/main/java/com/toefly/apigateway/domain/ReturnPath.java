package com.toefly.apigateway.domain;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Path of the web app to open after sign-in; only relative app paths, never another site (open redirect). */
public record ReturnPath(String value) {

    private static final Pattern SAFE = Pattern.compile("^/[A-Za-z0-9\\-._~/$?=&%]*$");

    public static final ReturnPath HOME = new ReturnPath("/");

    public ReturnPath {
        if (!isSafe(value)) {
            throw new IllegalArgumentException("Unsafe return path");
        }
    }

    public static ReturnPath parse(@Nullable String value) {
        return value != null && isSafe(value) ? new ReturnPath(value) : HOME;
    }

    private static boolean isSafe(String value) {
        return value.length() <= 512
                && SAFE.matcher(value).matches()
                && !value.startsWith("//")
                && !value.contains("/\\")
                && !value.startsWith("/auth/");
    }
}
