package com.toefly.planning.domain.composition;

import java.util.List;

/** The answer of the model in {@code WeekDraftOutput} (docs/06-ai-agent.md §5.2), before the validation. */
public record DraftLayout(List<Day> days, String weekGoal, String focusExplanation) {

    public DraftLayout {
        days = List.copyOf(days);
    }

    /** @param day {@code MON}…{@code SUN} */
    public record Day(String day, List<Pick> tasks) {

        public Day {
            tasks = List.copyOf(tasks);
        }
    }

    public record Pick(String candidateRef, String whyForYou) {}
}
