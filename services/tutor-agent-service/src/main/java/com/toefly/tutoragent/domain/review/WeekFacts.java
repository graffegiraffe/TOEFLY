package com.toefly.tutoragent.domain.review;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Everything the review may say, collected without AI (docs/06 §5.3): the model only words it, so every number and
 * example of the review must come from here.
 */
public record WeekFacts(
        int weekNo,
        int tasksTotal,
        int tasksDone,
        double progressPercent,
        @Nullable Integer activeMinutes,
        List<SectionLine> sections,
        List<ScoreLine> scores,
        Map<String, Double> bands,
        Map<String, Double> previousBands,
        List<MistakeLine> mistakes,
        List<String> previousTopMistakes,
        List<FocusCandidate> focusCandidates) {

    /** Fewer done tasks than this gives the short review of AC-18.01.5. */
    public static final int MIN_TASKS = 3;

    public WeekFacts {
        sections = List.copyOf(sections);
        scores = List.copyOf(scores);
        bands = Map.copyOf(bands);
        previousBands = Map.copyOf(previousBands);
        mistakes = List.copyOf(mistakes);
        previousTopMistakes = List.copyOf(previousTopMistakes);
        focusCandidates = List.copyOf(focusCandidates);
    }

    public boolean lowActivity() {
        return tasksDone < MIN_TASKS;
    }

    public record SectionLine(String section, int tasksTotal, int tasksDone, int minutesPlanned, int minutesDone) {}

    /** Closed task types carry accuracy, Writing and Speaking an average score 0–5. */
    public record ScoreLine(
            String taskType,
            int attempts,
            @Nullable Double accuracyPercent,
            @Nullable Double averageScore) {}

    public record MistakeLine(
            String subcategory,
            String category,
            String title,
            int count,
            List<Example> examples,
            List<String> unitRefs) {
        public MistakeLine {
            examples = List.copyOf(examples);
            unitRefs = List.copyOf(unitRefs);
        }
    }

    public record Example(String wrong, @Nullable String right) {}

    public record FocusCandidate(String subcategory, String title, double weight) {}

    public @Nullable Double bandDelta() {
        Double now = bands.get("OVERALL");
        Double before = previousBands.get("OVERALL");
        return now == null || before == null ? null : Math.round((now - before) * 10) / 10.0;
    }
}
