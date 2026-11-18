package com.toefly.assessment.domain.submission;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text of a Writing version: NFC, {@code \n} line ends, at most 5000 code points (INV-SUB-05). */
public final class ResponseText {

    public static final int MAX_CODE_POINTS = 5000;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}'’-]+");

    private ResponseText() {}

    public static String normalize(String text) {
        String normalized = Normalizer.normalize(text.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC);
        if (normalized.codePointCount(0, normalized.length()) > MAX_CODE_POINTS) {
            throw new SubmissionRuleException(
                    "validation-failed", "The response is longer than " + MAX_CODE_POINTS + " characters.");
        }
        return normalized;
    }

    public static int wordCount(String text) {
        Matcher words = WORD.matcher(text);
        int count = 0;
        while (words.find()) {
            count++;
        }
        return count;
    }
}
