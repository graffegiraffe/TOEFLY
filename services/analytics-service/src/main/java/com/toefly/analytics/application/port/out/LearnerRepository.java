package com.toefly.analytics.application.port.out;

import com.toefly.analytics.domain.learner.LearnerSettings;
import java.util.Optional;
import java.util.UUID;

/** {@code learner}: time zone, daily goal and exam goal of each learner. */
public interface LearnerRepository {

    Optional<LearnerSettings> find(UUID userId);

    void save(LearnerSettings settings);

    void reset();

    void deleteUser(UUID userId);
}
