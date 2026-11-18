package com.toefly.assessment.domain.grading;

import com.toefly.assessment.domain.submission.ResponseText;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic checks before the model (docs/06-ai-agent.md §5.4.1, §5.4.5): words, share of 5-grams copied
 * from the task, share of words that are not English, and heuristics of instructions inside the response.
 */
public record TextFacts(int wordCount, double copiedShare, double nonEnglishShare, boolean instructionSuspected) {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}'’-]+");
    private static final Pattern LATIN = Pattern.compile("[\\p{IsLatin}\\p{N}'’-]+");
    private static final Pattern INSTRUCTION =
            Pattern.compile("(?i)(ignore (all |the )?(previous|above|prior) (instructions|rules)"
                    + "|(give|award|assign) (me |this( response| essay)? )?(a )?(score|grade|band) of"
                    + "|you are (now )?(a|an|the) (?!student)\\w+ (that|who)"
                    + "|system prompt|disregard the rubric|score this (response|essay) (as )?[0-9])");

    public static TextFacts of(String response, List<String> taskTexts) {
        List<String> words = words(response);
        int latin = 0;
        for (String word : words) {
            if (LATIN.matcher(word).matches()) {
                latin++;
            }
        }
        double nonEnglish = words.isEmpty() ? 0 : 1.0 - (double) latin / words.size();
        return new TextFacts(
                ResponseText.wordCount(response),
                copiedShare(words, taskTexts),
                nonEnglish,
                INSTRUCTION.matcher(response).find());
    }

    private static double copiedShare(List<String> words, List<String> taskTexts) {
        if (words.size() < 5) {
            return 0;
        }
        Set<String> source = new HashSet<>();
        for (String text : taskTexts) {
            source.addAll(grams(words(text)));
        }
        List<String> own = grams(words);
        long copied = own.stream().filter(source::contains).count();
        return (double) copied / own.size();
    }

    private static List<String> grams(List<String> words) {
        List<String> grams = new ArrayList<>();
        for (int i = 0; i + 5 <= words.size(); i++) {
            grams.add(String.join(" ", words.subList(i, i + 5)));
        }
        return grams;
    }

    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            words.add(matcher.group().toLowerCase(Locale.ROOT).replace('’', '\''));
        }
        return words;
    }
}
