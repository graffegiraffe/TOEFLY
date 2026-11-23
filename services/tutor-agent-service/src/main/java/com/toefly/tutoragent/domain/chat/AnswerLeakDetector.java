package com.toefly.tutoragent.domain.chat;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The deterministic part of the output guard for unsubmitted tasks (docs/06 §4.8): no option named as the answer, no
 * restored words of the task, no ready-made response.
 */
public final class AnswerLeakDetector {

    private static final Pattern OPTION_AS_ANSWER = Pattern.compile(
            "(answer is|correct (answer|option|choice) is|choose|pick|select|it's|it is|go with) \\(?\\b[a-d]\\b\\)?"
                    + "|\\boption \\(?[a-d]\\)? is (correct|right)"
                    + "|\\b[a-d]\\) is (correct|right|the answer)");
    /** Longer stretches of connected English than this look like a written response. */
    static final int MAX_PROSE_WORDS = 40;

    private AnswerLeakDetector() {}

    /**
     * True when the answer gives away the unsubmitted task.
     *
     * @param taskWords words of the keys the learner must find (Complete the Words, Build a Sentence), if known
     * @param draft the learner's draft of a Writing task, if any
     */
    public static boolean leaks(String answer, ChatContextType context, List<String> taskWords, String draft) {
        if (!context.unsubmitted()) {
            return false;
        }
        String text = answer.toLowerCase(Locale.ROOT);
        if (OPTION_AS_ANSWER.matcher(text).find()) {
            return true;
        }
        if (!taskWords.isEmpty()
                && taskWords.stream().filter(w -> w.length() > 3).anyMatch(w -> containsWord(text, w))) {
            return true;
        }
        if (!draft.isBlank() && overlap(text, draft.toLowerCase(Locale.ROOT)) > 0.2) {
            return true;
        }
        return longestProse(answer) > MAX_PROSE_WORDS;
    }

    private static boolean containsWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word.toLowerCase(Locale.ROOT)) + "\\b")
                .matcher(text)
                .find();
    }

    /** Share of 5-grams of the answer that also occur in the draft. */
    static double overlap(String answer, String draft) {
        List<String> a = words(answer);
        if (a.size() < 5) {
            return 0;
        }
        Set<String> grams = new HashSet<>();
        List<String> d = words(draft);
        for (int i = 0; i + 5 <= d.size(); i++) {
            grams.add(String.join(" ", d.subList(i, i + 5)));
        }
        int total = a.size() - 4;
        int shared = 0;
        for (int i = 0; i < total; i++) {
            if (grams.contains(String.join(" ", a.subList(i, i + 5)))) {
                shared++;
            }
        }
        return (double) shared / total;
    }

    /** Words in the longest run without a line break, list marker or question. */
    static int longestProse(String answer) {
        int longest = 0;
        for (String block : answer.split("\\n|\\?|(?<=:)\\s")) {
            if (block.strip().startsWith("-") || block.strip().matches("^\\d+\\..*")) {
                continue;
            }
            longest = Math.max(longest, words(block).size());
        }
        return longest;
    }

    private static List<String> words(String text) {
        return List.of(text.replaceAll("[^\\p{L}\\p{N}' ]", " ").trim().split("\\s+")).stream()
                .filter(w -> !w.isBlank())
                .toList();
    }
}
