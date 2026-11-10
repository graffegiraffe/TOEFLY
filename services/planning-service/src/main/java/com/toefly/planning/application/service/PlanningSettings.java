package com.toefly.planning.application.service;

import com.toefly.planning.domain.composition.ResourceLink;
import com.toefly.planning.domain.week.TaskType;
import java.util.List;
import java.util.Set;

/**
 * Switches of this release (docs/12, phase 4, decisions 2, 3, 6).
 *
 * @param availableTypes item-set types the learner can open
 * @param examFormat format of the sets in the plan; legacy sets stay in Extra practice
 */
public record PlanningSettings(
        Set<TaskType> availableTypes,
        boolean vocabularyReview,
        boolean mockExam,
        String examFormat,
        List<ResourceLink> resources) {

    public PlanningSettings {
        availableTypes = Set.copyOf(availableTypes);
        resources = List.copyOf(resources);
    }
}
