package com.toefly.planning.application.port.out;

import com.toefly.planning.domain.plan.StudyPlan;
import java.util.Optional;
import java.util.UUID;

public interface StudyPlanRepository {

    Optional<StudyPlan> findByUser(UUID userId);

    Optional<StudyPlan> findById(UUID planId);

    void insert(StudyPlan plan);

    /** Writes the plan when its version is unchanged and returns the new version. */
    int update(StudyPlan plan);

    void deleteByUser(UUID userId);
}
