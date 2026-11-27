package com.toefly.tutoragent.domain.review;

import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A weekly review (docs/03 §11, docs/06 §5.3); at most one per week, a regeneration overwrites it. */
public record WeeklyReview(
        UUID id,
        UUID userId,
        UUID weekId,
        int weekNo,
        Instant generatedAt,
        ExplanationLanguage language,
        boolean aiFallback,
        boolean lowActivity,
        Text text,
        WeekFacts facts,
        @Nullable String tutorNote) {

    /** The worded part: from the model or from the template. */
    public record Text(
            String headline,
            String summary,
            List<TopMistake> topMistakes,
            List<String> strengths,
            List<Focus> nextWeekFocus,
            String motivation) {
        public Text {
            topMistakes = List.copyOf(topMistakes);
            strengths = List.copyOf(strengths);
            nextWeekFocus = List.copyOf(nextWeekFocus);
        }
    }

    public record TopMistake(
            String subcategory,
            int count,
            String pattern,
            WeekFacts.@Nullable Example example,
            String advice,
            List<String> unitRefs) {
        public TopMistake {
            unitRefs = List.copyOf(unitRefs);
        }
    }

    public record Focus(String subcategory, String why) {}
}
