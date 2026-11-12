package com.toefly.planning.domain.composition;

import com.toefly.planning.domain.week.TaskType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Everything the composition of one week needs.
 *
 * @param weeksLeft weeks of the plan from this one to the last, at least 1; the fill step shares the bank among them
 * @param weakTypes weak task types, the weakest first
 * @param availableTypes item-set types the learner can open in this release
 * @param recentTypeCounts tasks of each type in the last four weeks, for the rotation of the fill step
 */
public record CompositionRequest(
        int weekNo,
        int weeksLeft,
        int minutesPerDay,
        List<TaskType> weakTypes,
        Set<TaskType> availableTypes,
        boolean vocabularyReview,
        boolean mockExam,
        Optional<UnitRef> roundUpUnit,
        Optional<UnitRef> collinsGrammarUnit,
        Optional<UnitRef> collinsVocabularyUnit,
        List<ResourceLink> resources,
        Map<TaskType, Integer> recentTypeCounts,
        SetSupply supply) {

    public CompositionRequest {
        weeksLeft = Math.max(1, weeksLeft);
        weakTypes = List.copyOf(weakTypes);
        availableTypes = Set.copyOf(availableTypes);
        resources = List.copyOf(resources);
        recentTypeCounts = Map.copyOf(recentTypeCounts);
    }
}
