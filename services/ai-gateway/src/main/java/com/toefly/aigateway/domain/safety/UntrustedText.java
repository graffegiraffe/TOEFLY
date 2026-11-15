package com.toefly.aigateway.domain.safety;

import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wraps the learner's text in a tag with a one-time nonce so instructions inside it stay data (§3.5.1); any tag of
 * the same name inside the text is escaped first.
 */
public final class UntrustedText {

    private static final char[] ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private UntrustedText() {}

    public static String nonce() {
        char[] nonce = new char[8];
        for (int i = 0; i < nonce.length; i++) {
            nonce[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(nonce);
    }

    public static String wrap(String tag, String nonce, String text) {
        Pattern sameTag = Pattern.compile("<(/?)(\\s*" + Pattern.quote(tag) + ")", Pattern.CASE_INSENSITIVE);
        String escaped =
                sameTag.matcher(text).replaceAll(m -> Matcher.quoteReplacement("&lt;" + m.group(1) + m.group(2)));
        return "<" + tag + " id=\"" + nonce + "\">" + escaped + "</" + tag + ">";
    }
}
