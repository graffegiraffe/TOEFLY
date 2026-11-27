package com.toefly.tutoragent.domain.reengagement;

import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import com.toefly.tutoragent.domain.review.Emoji;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A come-back message: limits, banned pressure words, no emoji, only numbers of the data (docs/06 §5.10). */
public record ComeBackMessage(String title, String body, String ctaLabel, String deepLink) {

    static final List<String> BANNED = List.of("lazy", "failed", "disappointed", "should have", "must");
    private static final Pattern NUMBER = Pattern.compile("\\d+(\\.\\d+)?");

    public List<String> violations(Set<String> allowedNumbers) {
        List<String> problems = new ArrayList<>();
        if (title.isBlank() || title.length() > 60) {
            problems.add("title must have 1 to 60 characters");
        }
        if (body.isBlank() || body.length() > 240) {
            problems.add("body must have 1 to 240 characters");
        }
        if (ctaLabel.isBlank() || ctaLabel.length() > 24) {
            problems.add("ctaLabel must have 1 to 24 characters");
        }
        String all = (title + " " + body + " " + ctaLabel).toLowerCase(Locale.ROOT);
        BANNED.stream()
                .filter(w -> Pattern.compile("\\b" + w + "\\b").matcher(all).find())
                .forEach(w -> problems.add("do not use '" + w + "'"));
        if (Emoji.contains(all)) {
            problems.add("no emoji");
        }
        if (all.chars().filter(c -> c == '!').count() > 1) {
            problems.add("at most one exclamation mark");
        }
        Matcher numbers = NUMBER.matcher(title + " " + body);
        while (numbers.find()) {
            if (!allowedNumbers.contains(numbers.group())) {
                problems.add("the number " + numbers.group() + " is not in the data");
            }
        }
        return problems;
    }

    /** The message without AI, in the learner's language. */
    public static ComeBackMessage template(
            Situation situation, String nextTask, int nextMinutes, ExplanationLanguage language) {
        boolean ru = language == ExplanationLanguage.RU;
        String step = nextTask.isBlank()
                ? (ru ? "Начните с короткого задания на сегодня." : "Start with a short task from today's plan.")
                : (ru
                        ? "Следующий шаг: " + nextTask + ", " + nextMinutes + " мин."
                        : "Next step: " + nextTask + ", " + nextMinutes + " min.");
        String title = switch (situation) {
            case AWAY_2_DAYS -> ru ? "Продолжим подготовку?" : "Ready to pick up where you left off?";
            case AWAY_7_DAYS -> ru ? "Ваш план ждёт вас" : "Your plan is waiting for you";
            case WEEK_BEHIND -> ru ? "Неделя пересобрана под вас" : "Your week has been adjusted";
            case EXAM_SOON_INACTIVE -> ru ? "Экзамен уже скоро" : "Your exam is coming up";
        };
        return new ComeBackMessage(title, step, ru ? "Открыть план" : "Open today", "toefly://today");
    }
}
