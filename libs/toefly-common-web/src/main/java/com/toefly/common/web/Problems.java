package com.toefly.common.web;

import java.net.URI;
import java.util.Locale;

/** Helpers for catalog problem types. */
public final class Problems {

    public static final String BASE_URI = "https://toefly.app/problems/";

    private Problems() {}

    public static URI type(String slug) {
        return URI.create(BASE_URI + slug);
    }

    public static String code(String slug) {
        return slug.replace('-', '_').toUpperCase(Locale.ROOT);
    }

    public static String title(String slug) {
        String words = slug.replace('-', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
