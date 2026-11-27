package com.toefly.tutoragent.domain.learner;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Subcategory of a wrong answer to a closed item (docs/06 §6.2). Items carry no question subtype yet, so Reading and
 * Listening questions fall back to {@code COMPREHENSION.DETAIL} (docs/12, phase 5c, decision 12).
 */
public final class ClosedItemClassifier {

    private ClosedItemClassifier() {}

    /** Subcategory of the skill the item trains: one opportunity per answered item. */
    public static String skillOf(String taskType, @Nullable String key) {
        return switch (taskType) {
            case "COMPLETE_THE_WORDS" -> "COMPREHENSION.WORD_COMPLETION";
            case "BUILD_A_SENTENCE" -> isQuestion(key) ? "GRAMMAR.QUESTIONS_WORD_ORDER" : "GRAMMAR.WORD_ORDER";
            case "LISTEN_CHOOSE_RESPONSE" -> "COMPREHENSION.RESPONSE_APPROPRIATENESS";
            default -> "COMPREHENSION.DETAIL";
        };
    }

    /** Subcategory of a wrong answer; {@code answer} and {@code key} are the typed or assembled texts if any. */
    public static String mistakeOf(String taskType, @Nullable String answer, @Nullable String key) {
        if (taskType.equals("COMPLETE_THE_WORDS") && answer != null && key != null) {
            return completeTheWords(answer, key);
        }
        return skillOf(taskType, key);
    }

    static String completeTheWords(String answer, String key) {
        String a = answer.strip().toLowerCase(Locale.ROOT);
        String k = key.strip().toLowerCase(Locale.ROOT);
        if (a.isEmpty()) {
            return "COMPREHENSION.WORD_COMPLETION";
        }
        if (k.length() >= 5 && distance(a, k) <= 2 && !sameStem(a, k)) {
            return "SPELLING.COMMON";
        }
        if (sameStem(a, k)) {
            if (a.equals(k + "s") || k.equals(a + "s")) {
                return "GRAMMAR.SUBJECT_VERB_AGREEMENT";
            }
            if (a.endsWith("ed") || k.endsWith("ed") || a.endsWith("ing") || k.endsWith("ing")) {
                return "GRAMMAR.VERB_TENSES";
            }
            if (a.endsWith("ly") || k.endsWith("ly")) {
                return "VOCABULARY.WORD_FORM";
            }
            return "GRAMMAR.NOUN_NUMBER";
        }
        return "COMPREHENSION.WORD_COMPLETION";
    }

    private static boolean isQuestion(@Nullable String key) {
        return key != null && key.strip().endsWith("?");
    }

    /** Same lemma, crudely: one word is the other plus a known ending. */
    private static boolean sameStem(String a, String b) {
        if (a.equals(b)) {
            return false;
        }
        String stemA = stem(a);
        return stemA.length() >= 3 && stemA.equals(stem(b));
    }

    private static String stem(String word) {
        if (word.length() > 4 && word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        for (String ending : new String[] {"ing", "ed", "es", "ly", "s"}) {
            if (word.length() > ending.length() + 2 && word.endsWith(ending)) {
                return word.substring(0, word.length() - ending.length());
            }
        }
        return word;
    }

    /** Damerau–Levenshtein distance (optimal string alignment). */
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
