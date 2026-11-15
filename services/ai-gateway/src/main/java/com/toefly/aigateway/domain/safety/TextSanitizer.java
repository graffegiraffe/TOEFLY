package com.toefly.aigateway.domain.safety;

/**
 * Removes invisible characters that hide instructions (§3.5.2): zero-width and direction marks U+200B–U+200F, word
 * joiners U+2060–U+2064, the byte order mark and the tag block U+E0000–U+E007F.
 */
public final class TextSanitizer {

    private TextSanitizer() {}

    public static boolean hasInvisible(String text) {
        return text.codePoints().anyMatch(TextSanitizer::invisible);
    }

    public static String stripInvisible(String text) {
        if (!hasInvisible(text)) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().filter(cp -> !invisible(cp)).forEach(out::appendCodePoint);
        return out.toString();
    }

    static boolean invisible(int cp) {
        return (cp >= 0x200B && cp <= 0x200F)
                || (cp >= 0x2060 && cp <= 0x2064)
                || cp == 0xFEFF
                || (cp >= 0xE0000 && cp <= 0xE007F);
    }
}
