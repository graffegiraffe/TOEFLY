package com.toefly.assessment.application.port.out;

import com.toefly.assessment.domain.estimate.BandEstimate;
import com.toefly.assessment.domain.estimate.EstimateInputs;
import com.toefly.assessment.domain.estimate.Section;
import com.toefly.assessment.domain.estimate.WeeklyEstimate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Projections the estimate is computed from and the stored estimates (current and one per ISO week). */
public interface EstimateInputsRepository {

    void saveAbility(UUID userId, Section section, double theta, double standardError, int n, Instant at);

    void addBuildSentenceItems(UUID userId, UUID attemptId, List<Boolean> correct, Instant at);

    void onboardingCompleted(UUID userId, Instant at);

    /** Inputs except Writing scores, which come from {@link SubmissionRepository#firstVersionScores}. */
    EstimateInputs load(UUID userId, Instant asOf);

    void saveEstimate(UUID userId, BandEstimate estimate, LocalDate weekStart);

    Optional<BandEstimate> current(UUID userId);

    List<WeeklyEstimate> history(UUID userId, int limit);
}
