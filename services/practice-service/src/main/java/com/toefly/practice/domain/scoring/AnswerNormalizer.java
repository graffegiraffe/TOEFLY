package com.toefly.practice.domain.scoring;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalization of typed answers: case, extra spaces and typographic apostrophes (AC-14.04.2, AC-07.01.4). */
public final class AnswerNormalizer {

    private static final Pattern APOSTROPHES = Pattern.compile("[‘’ʼ`´]");
    private static final Pattern DASHES = Pattern.compile("[‐‑‒–—]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private AnswerNormalizer() {}

    public static String normalize(String text) {
        String value = APOSTROPHES.matcher(text).replaceAll("'");
        value = DASHES.matcher(value).replaceAll("-");
        value = SPACES.matcher(value.strip()).replaceAll(" ");
        return value.toLowerCase(Locale.ROOT);
    }
}
