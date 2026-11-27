package com.toefly.planning.domain.learner;

import java.util.List;
import java.util.Locale;

/**
 * One mistake subcategory of the learner model (docs/06-ai-agent.md §6.2).
 *
 * @param category {@code MistakeCategory}, for example {@code GRAMMAR}
 * @param subcategory the code {@code <CATEGORY>.<NAME>}; a bare name gets the category as its prefix
 * @param priority ranking weight of tutor-agent: weight × category impact × status factor
 * @param status {@code ACTIVE}, {@code WATCH} or {@code RESOLVED}
 * @param occurrencesLast7Days mistakes of the last 7 days (AC-14.05.2)
 * @param unitRefs textbook units, for example {@code ROUNDUP5.U10}
 */
public record MistakeStat(
        String category,
        String subcategory,
        double priority,
        String status,
        int occurrencesLast7Days,
        List<String> unitRefs) {

    /** Mistakes of a grammar topic in a week that add AI practice for its unit (AC-14.05.2). */
    public static final int PRACTICE_THRESHOLD = 3;

    public MistakeStat {
        category = category.toUpperCase(Locale.ROOT);
        subcategory = subcategory.contains(".") ? subcategory : category + "." + subcategory;
        status = status.toUpperCase(Locale.ROOT);
        unitRefs = List.copyOf(unitRefs);
    }

    public boolean grammar() {
        return category.equals("GRAMMAR");
    }

    /** A grammar topic met at least 3 times in the last 7 days (AC-14.05.2). */
    public boolean needsGrammarPractice() {
        return grammar() && occurrencesLast7Days >= PRACTICE_THRESHOLD;
    }

    public boolean resolved() {
        return status.equals("RESOLVED");
    }
}
