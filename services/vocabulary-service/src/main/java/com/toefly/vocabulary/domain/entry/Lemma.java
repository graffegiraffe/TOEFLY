package com.toefly.vocabulary.domain.entry;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Dictionary form of a selected term: trimmed of surrounding punctuation, lower case (proper nouns are rare in the
 * academic word list), single spaces, typographic apostrophes folded. Inflections are reduced by the word card of
 * ai-gateway; without it the selected form is kept.
 */
public final class Lemma {

    public static final int MAX_LENGTH = 80;
    private static final Pattern WORD = Pattern.compile("[\\p{L}][\\p{L}\\p{M}'’\\- ]*[\\p{L}\\p{M}]|[\\p{L}]");
    private static final Pattern EDGES = Pattern.compile("^[^\\p{L}]+|[^\\p{L}]+$");

    private Lemma() {}

    public static String normalize(String term) {
        if (term == null) {
            throw new InvalidTermException("The term is empty.");
        }
        String text = Normalizer.normalize(term, Normalizer.Form.NFC)
                .replace('’', '\'')
                .replaceAll("\\s+", " ")
                .strip();
        text = EDGES.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            throw new InvalidTermException("The term is empty.");
        }
        if (text.length() > MAX_LENGTH) {
            throw new InvalidTermException("The term is longer than " + MAX_LENGTH + " characters.");
        }
        if (!WORD.matcher(text).matches() || text.split(" ").length > 4) {
            throw new InvalidTermException("Select a word or a short phrase (up to 4 words).");
        }
        return text;
    }
}
