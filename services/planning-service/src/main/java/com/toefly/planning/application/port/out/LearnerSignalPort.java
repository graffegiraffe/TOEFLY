package com.toefly.planning.application.port.out;

import com.toefly.planning.domain.composition.WeakTypes.Accuracy;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.WeekSection;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** What planning knows about the learner: results of closed sets and θ by section. */
public interface LearnerSignalPort {

    void recordResult(UUID userId, UUID attemptId, TaskType type, int correct, int total, Instant scoredAt);

    Map<TaskType, Accuracy> accuracySince(UUID userId, Instant since);

    /** Keeps the newer θ of a section; an older event changes nothing. */
    void recordTheta(UUID userId, WeekSection section, double theta, Instant occurredAt);

    Map<WeekSection, Double> theta(UUID userId);

    void deleteByUser(UUID userId);
}
