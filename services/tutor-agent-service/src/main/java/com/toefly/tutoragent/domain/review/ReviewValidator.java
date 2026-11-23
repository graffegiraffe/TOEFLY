package com.toefly.tutoragent.domain.review;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks a worded review against its facts (docs/06 §5.3): subcategories and counts from the input, examples quoted
 * from the input, focus from the candidates, every number of the summary present in the input, no emoji.
 */
public final class ReviewValidator {

    private static final Pattern NUMBER = Pattern.compile("\\d+(\\.\\d+)?%?");

    private ReviewValidator() {}

    /** Violations in plain English, empty when the text is valid. */
    public static List<String> violations(WeeklyReview.Text text, WeekFacts facts) {
        List<String> problems = new ArrayList<>();
        Map<String, WeekFacts.MistakeLine> mistakes = new java.util.HashMap<>();
        facts.mistakes().forEach(m -> mistakes.put(m.subcategory(), m));
        Set<String> seen = new HashSet<>();
        for (WeeklyReview.TopMistake m : text.topMistakes()) {
            WeekFacts.MistakeLine line = mistakes.get(m.subcategory());
            if (line == null) {
                problems.add("topMistakes: " + m.subcategory() + " is not in MISTAKES");
                continue;
            }
            if (!seen.add(m.subcategory())) {
                problems.add("topMistakes: " + m.subcategory() + " is repeated");
            }
            if (m.count() != line.count()) {
                problems.add("topMistakes: count of " + m.subcategory() + " must be " + line.count());
            }
            if (m.example() != null && !quoted(m.example().wrong(), line)) {
                problems.add("topMistakes: example of " + m.subcategory() + " is not quoted from its examples");
            }
        }
        if (text.topMistakes().size() > 5) {
            problems.add("topMistakes: at most 5");
        }
        Set<String> candidates = new HashSet<>();
        facts.focusCandidates().forEach(c -> candidates.add(c.subcategory()));
        for (WeeklyReview.Focus f : text.nextWeekFocus()) {
            if (!candidates.contains(f.subcategory())) {
                problems.add("nextWeekFocus: " + f.subcategory() + " is not in NEXT_FOCUS_CANDIDATES");
            }
        }
        if (text.strengths().isEmpty()) {
            problems.add("strengths: at least one");
        }
        Set<String> numbers = numbersOf(facts);
        Matcher matcher = NUMBER.matcher(text.summary() + " " + text.headline());
        while (matcher.find()) {
            String number = matcher.group().replace("%", "");
            if (!numbers.contains(number)) {
                problems.add("summary: the number " + matcher.group() + " is not in STATS or SCORES");
            }
        }
        if (Emoji.contains(text.headline() + text.summary() + text.motivation())) {
            problems.add("no emoji");
        }
        return problems;
    }

    private static boolean quoted(String wrong, WeekFacts.MistakeLine line) {
        String needle = normalize(wrong);
        if (needle.isEmpty()) {
            return false;
        }
        return line.examples().stream().anyMatch(e -> normalize(e.wrong()).contains(needle));
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("[\"“”']", "")
                .strip();
    }

    /** Numbers the review may use: counts, percents, minutes, scores and bands of the input, and the week number. */
    static Set<String> numbersOf(WeekFacts facts) {
        Set<String> numbers = new HashSet<>();
        add(numbers, facts.weekNo());
        add(numbers, facts.tasksTotal());
        add(numbers, facts.tasksDone());
        add(numbers, facts.progressPercent());
        if (facts.activeMinutes() != null) {
            add(numbers, facts.activeMinutes());
        }
        for (WeekFacts.SectionLine s : facts.sections()) {
            add(numbers, s.tasksTotal());
            add(numbers, s.tasksDone());
            add(numbers, s.minutesPlanned());
            add(numbers, s.minutesDone());
        }
        for (WeekFacts.ScoreLine s : facts.scores()) {
            add(numbers, s.attempts());
            if (s.accuracyPercent() != null) {
                add(numbers, s.accuracyPercent());
            }
            if (s.averageScore() != null) {
                add(numbers, s.averageScore());
            }
        }
        facts.bands().values().forEach(v -> add(numbers, v));
        facts.previousBands().values().forEach(v -> add(numbers, v));
        if (facts.bandDelta() != null) {
            add(numbers, Math.abs(facts.bandDelta()));
        }
        facts.mistakes().forEach(m -> add(numbers, m.count()));
        add(numbers, facts.weekNo() + 1);
        return numbers;
    }

    private static void add(Set<String> numbers, double value) {
        long rounded = Math.round(value);
        numbers.add(Long.toString(rounded));
        double oneDecimal = Math.round(value * 10) / 10.0;
        numbers.add(
                oneDecimal == Math.rint(oneDecimal) ? Long.toString((long) oneDecimal) : Double.toString(oneDecimal));
        numbers.add(String.format(Locale.ROOT, "%.1f", value));
    }
}
