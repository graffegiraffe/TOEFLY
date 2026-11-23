package com.toefly.tutoragent.domain.review;

import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The review without AI (docs/06 §5.3, AC-18.01.4, AC-18.01.5): the same facts in fixed sentences, used for weeks with
 * little activity and when the model fails twice or stays unavailable.
 */
public final class WeeklyReviewTemplate {

    private WeeklyReviewTemplate() {}

    public static WeeklyReview.Text of(WeekFacts facts, ExplanationLanguage language) {
        boolean ru = language == ExplanationLanguage.RU;
        int percent = (int) Math.round(facts.progressPercent());
        if (facts.lowActivity()) {
            return new WeeklyReview.Text(
                    ru ? "Not enough activity this week" : "Not enough activity this week",
                    ru
                            ? "На этой неделе выполнено " + facts.tasksDone() + " из " + facts.tasksTotal()
                                    + " заданий. Для честного разбора нужно хотя бы " + WeekFacts.MIN_TASKS + "."
                            : "You finished " + facts.tasksDone() + " of " + facts.tasksTotal()
                                    + " tasks this week. A full review needs at least " + WeekFacts.MIN_TASKS + ".",
                    List.of(),
                    strengths(facts, ru),
                    focus(facts, ru),
                    ru
                            ? "Выделите 20 минут в день на этой неделе: короткие занятия каждый день дают больше, чем одно длинное."
                            : "Plan 20 minutes a day next week: short daily sessions work better than one long one.");
        }
        List<WeeklyReview.TopMistake> top = new ArrayList<>();
        for (WeekFacts.MistakeLine m : facts.mistakes().stream().limit(5).toList()) {
            WeekFacts.Example example =
                    m.examples().isEmpty() ? null : m.examples().getFirst();
            top.add(new WeeklyReview.TopMistake(
                    m.subcategory(),
                    m.count(),
                    m.title(),
                    example,
                    ru
                            ? "Повторите правило и сделайте упражнения юнита по этой теме."
                            : "Review the rule and do the exercises of the linked unit.",
                    m.unitRefs()));
        }
        String headline = ru
                ? "Week " + facts.weekNo() + ": " + percent + "% done"
                : "Week " + facts.weekNo() + ": " + percent + "% done";
        String summary = ru
                ? "Выполнено " + facts.tasksDone() + " из " + facts.tasksTotal() + " заданий (" + percent + "%)."
                : "You finished " + facts.tasksDone() + " of " + facts.tasksTotal() + " tasks (" + percent + "%).";
        return new WeeklyReview.Text(
                headline,
                summary,
                top,
                strengths(facts, ru),
                focus(facts, ru),
                ru ? "Хорошая работа — продолжайте в том же темпе." : "Keep the same pace next week.");
    }

    private static List<String> strengths(WeekFacts facts, boolean ru) {
        List<String> strengths = new ArrayList<>();
        facts.scores().stream()
                .filter(s -> s.accuracyPercent() != null && s.accuracyPercent() >= 70)
                .sorted(Comparator.comparing(WeekFacts.ScoreLine::accuracyPercent)
                        .reversed())
                .limit(2)
                .forEach(s -> strengths.add((ru ? "Высокая точность: " : "Strong accuracy in ") + label(s.taskType())
                        + " (" + Math.round(s.accuracyPercent()) + "%)"));
        facts.sections().stream()
                .filter(s -> s.tasksTotal() > 0 && s.tasksDone() == s.tasksTotal())
                .limit(2 - Math.min(2, strengths.size()))
                .forEach(s -> strengths.add(
                        (ru ? "Раздел выполнен полностью: " : "All tasks done in ") + label(s.section())));
        if (strengths.isEmpty()) {
            strengths.add(ru ? "Вы вернулись к занятиям на этой неделе" : "You kept studying this week");
        }
        return strengths;
    }

    private static List<WeeklyReview.Focus> focus(WeekFacts facts, boolean ru) {
        return facts.focusCandidates().stream()
                .limit(3)
                .map(c -> new WeeklyReview.Focus(
                        c.subcategory(), ru ? "Частая ошибка последних недель" : "A recurring mistake of recent weeks"))
                .toList();
    }

    static String label(String code) {
        String words = code.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
