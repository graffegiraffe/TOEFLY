package com.toefly.planning.domain.composition;

import com.toefly.planning.domain.week.WeekTask;
import java.util.List;

/**
 * A task the model may put into the week (docs/06-ai-agent.md §5.2): {@code ref} is the only id the model sees.
 *
 * @param task the pending task with its template "why for you"; WeekGenerator replaces the text and the day
 * @param mandatory the model must include it: book units, reviews, the mock exam, grammar practice (AC-14.05.2)
 * @param baseline the rules would put it into the week; GreedyWeekPacker takes exactly these
 * @param skills focus subcategories the task practises
 */
public record WeekCandidate(String ref, WeekTask task, boolean mandatory, boolean baseline, List<String> skills) {

    public WeekCandidate {
        skills = List.copyOf(skills);
    }

    public double minutes() {
        return task.estimatedMinutes();
    }
}
