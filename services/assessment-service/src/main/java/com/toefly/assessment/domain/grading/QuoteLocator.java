package com.toefly.assessment.domain.grading;

import java.util.Optional;

/**
 * Finds the quote of a comment in the response (docs/06-ai-agent.md §5.4.5): exact, then after normalising spaces
 * and typographic quotes, then fuzzy (Levenshtein ≤ 10% of the quote over a sliding window). Offsets are UTF-16
 * units of the original text, as in JavaScript and Swift {@code String.utf16}.
 */
public final class QuoteLocator {

    public record Range(int start, int end) {}

    private QuoteLocator() {}

    public static Optional<Range> locate(String text, String quote, int occurrence) {
        if (quote.isBlank() || text.isEmpty()) {
            return Optional.empty();
        }
        Optional<Range> exact = exact(text, quote, occurrence);
        if (exact.isPresent()) {
            return exact;
        }
        Optional<Range> normalized = normalized(text, quote, occurrence);
        if (normalized.isPresent()) {
            return normalized;
        }
        return fuzzy(text, quote.strip());
    }

    private static Optional<Range> exact(String text, String quote, int occurrence) {
        int from = 0;
        int found = -1;
        int first = -1;
        for (int i = 0; i < occurrence; i++) {
            found = text.indexOf(quote, from);
            if (found < 0) {
                break;
            }
            if (first < 0) {
                first = found;
            }
            from = found + 1;
        }
        int at = found >= 0 ? found : first;
        return at < 0 ? Optional.empty() : Optional.of(new Range(at, at + quote.length()));
    }

    private static Optional<Range> normalized(String text, String quote, int occurrence) {
        Normalized t = Normalized.of(text);
        Normalized q = Normalized.of(quote.strip());
        if (q.text.isEmpty()) {
            return Optional.empty();
        }
        return exact(t.text, q.text, occurrence).map(r -> new Range(t.map[r.start()], t.map[r.end() - 1] + 1));
    }

    private static Optional<Range> fuzzy(String text, String quote) {
        int n = quote.length();
        int limit = Math.max(1, n / 10);
        if (n < 8) {
            return Optional.empty();
        }
        int bestStart = -1;
        int bestEnd = -1;
        int bestDistance = limit + 1;
        for (int start = 0; start < text.length(); start++) {
            if (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) {
                continue;
            }
            for (int len = Math.max(1, n - limit); len <= n + limit && start + len <= text.length(); len++) {
                int distance = bounded(text, start, start + len, quote, bestDistance - 1);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestStart = start;
                    bestEnd = start + len;
                }
            }
        }
        return bestStart < 0 ? Optional.empty() : Optional.of(new Range(bestStart, bestEnd));
    }

    /** Levenshtein of text[from, to) and quote, or {@code max + 1} as soon as it surely exceeds {@code max}. */
    static int bounded(String text, int from, int to, String quote, int max) {
        if (max < 0) {
            return 1;
        }
        int m = to - from;
        int n = quote.length();
        if (Math.abs(m - n) > max) {
            return max + 1;
        }
        int[] previous = new int[n + 1];
        int[] current = new int[n + 1];
        for (int j = 0; j <= n; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= m; i++) {
            current[0] = i;
            int rowMin = current[0];
            char c = text.charAt(from + i - 1);
            for (int j = 1; j <= n; j++) {
                int cost = c == quote.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
                rowMin = Math.min(rowMin, current[j]);
            }
            if (rowMin > max) {
                return max + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[n];
    }

    /** Text with runs of whitespace collapsed and typographic quotes made plain; {@code map} points back. */
    private record Normalized(String text, int[] map) {

        static Normalized of(String source) {
            StringBuilder out = new StringBuilder(source.length());
            int[] map = new int[source.length()];
            boolean space = false;
            for (int i = 0; i < source.length(); i++) {
                char c = source.charAt(i);
                if (Character.isWhitespace(c)) {
                    if (space || out.isEmpty()) {
                        continue;
                    }
                    space = true;
                    c = ' ';
                } else {
                    space = false;
                    c = switch (c) {
                        case '‘', '’', 'ʼ' -> '\'';
                        case '“', '”', '„' -> '"';
                        case '–', '—' -> '-';
                        default -> c;
                    };
                }
                map[out.length()] = i;
                out.append(c);
            }
            int length = out.length();
            if (length > 0 && out.charAt(length - 1) == ' ') {
                out.setLength(length - 1);
            }
            return new Normalized(out.toString(), map);
        }
    }
}
