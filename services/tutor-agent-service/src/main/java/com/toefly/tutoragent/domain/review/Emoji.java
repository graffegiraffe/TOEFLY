package com.toefly.tutoragent.domain.review;

import java.util.regex.Pattern;

/** Emoji and pictographs; answers and reviews carry none (docs/06 §4.1). */
public final class Emoji {

    private static final Pattern EMOJI = Pattern.compile(
            "[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{1F1E6}-\\x{1F1FF}\\x{FE0F}\\x{200D}\\x{2B00}-\\x{2BFF}]");

    private Emoji() {}

    public static boolean contains(String text) {
        return EMOJI.matcher(text).find();
    }

    public static String strip(String text) {
        return EMOJI.matcher(text).replaceAll("");
    }
}
