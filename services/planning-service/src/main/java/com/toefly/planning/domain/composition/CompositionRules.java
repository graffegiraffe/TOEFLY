package com.toefly.planning.domain.composition;

import com.toefly.planning.domain.week.TaskType;
import java.util.EnumMap;
import java.util.Map;

/**
 * Parameters of the week composition (docs/03-domain-model.md §10.1, file {@code week-composition.yaml}).
 *
 * @param baseCounts tasks of each exam type in a week of 60 minutes a day
 * @param minutes estimated minutes of a set when the catalog does not know them
 */
public record CompositionRules(
        int baseMinutesPerDay,
        Map<TaskType, Integer> baseCounts,
        Map<TaskType, Double> minutes,
        double weakFactor,
        double budgetMaxFactor,
        double budgetMinFactor,
        double unitMinutes,
        double vocabularyReviewMinutes,
        double resourceMinutes,
        double mockExamMinutes,
        int minimalModeMinutes) {

    public CompositionRules {
        baseCounts = Map.copyOf(baseCounts);
        minutes = Map.copyOf(minutes);
    }

    /** The values of docs/02 §4.0 and docs/03 §10.1. */
    public static CompositionRules standard() {
        Map<TaskType, Integer> counts = new EnumMap<>(TaskType.class);
        counts.put(TaskType.COMPLETE_THE_WORDS, 3);
        counts.put(TaskType.READ_DAILY_LIFE, 2);
        counts.put(TaskType.READ_ACADEMIC_PASSAGE, 2);
        counts.put(TaskType.LISTEN_CHOOSE_RESPONSE, 2);
        counts.put(TaskType.LISTEN_CONVERSATION, 2);
        counts.put(TaskType.LISTEN_ANNOUNCEMENT, 2);
        counts.put(TaskType.LISTEN_ACADEMIC_TALK, 2);
        counts.put(TaskType.BUILD_A_SENTENCE, 2);
        counts.put(TaskType.WRITE_EMAIL, 1);
        counts.put(TaskType.WRITE_ACADEMIC_DISCUSSION, 1);
        counts.put(TaskType.LISTEN_AND_REPEAT, 2);
        counts.put(TaskType.TAKE_AN_INTERVIEW, 2);
        Map<TaskType, Double> minutes = new EnumMap<>(TaskType.class);
        minutes.put(TaskType.COMPLETE_THE_WORDS, 3.0);
        minutes.put(TaskType.READ_DAILY_LIFE, 2.5);
        minutes.put(TaskType.READ_ACADEMIC_PASSAGE, 6.0);
        minutes.put(TaskType.LISTEN_CHOOSE_RESPONSE, 2.5);
        minutes.put(TaskType.LISTEN_CONVERSATION, 2.0);
        minutes.put(TaskType.LISTEN_ANNOUNCEMENT, 2.0);
        minutes.put(TaskType.LISTEN_ACADEMIC_TALK, 3.5);
        minutes.put(TaskType.BUILD_A_SENTENCE, 6.0);
        minutes.put(TaskType.WRITE_EMAIL, 7.0);
        minutes.put(TaskType.WRITE_ACADEMIC_DISCUSSION, 10.0);
        minutes.put(TaskType.LISTEN_AND_REPEAT, 3.0);
        minutes.put(TaskType.TAKE_AN_INTERVIEW, 4.0);
        minutes.put(TaskType.GRAMMAR_EXERCISE, 8.0);
        minutes.put(TaskType.VOCAB_EXERCISE, 8.0);
        return new CompositionRules(60, counts, minutes, 1.5, 1.1, 0.9, 60, 5, 15, 90, 15);
    }

    public double minutesOf(TaskType type) {
        return minutes.getOrDefault(type, 5.0);
    }

    public int baseCount(TaskType type) {
        return baseCounts.getOrDefault(type, 0);
    }
}
