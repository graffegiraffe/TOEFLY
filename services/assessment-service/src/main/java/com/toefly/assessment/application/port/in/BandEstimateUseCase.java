package com.toefly.assessment.application.port.in;

import com.toefly.assessment.application.port.out.Cause;
import com.toefly.assessment.application.port.out.EstimateInputsRepository;
import com.toefly.assessment.domain.estimate.BandEstimate;
import com.toefly.assessment.domain.estimate.Section;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The band estimate (docs/03-domain-model.md §8) and the signals it is recomputed from. */
public interface BandEstimateUseCase {

    BandEstimate current(Caller caller, @Nullable UUID studentId);

    List<EstimateInputsRepository.WeeklyEstimate> history(Caller caller, @Nullable UUID studentId);

    void abilityUpdated(
            UUID userId, Section section, double theta, double standardError, int n, Instant at, Cause cause);

    void buildSentenceScored(UUID userId, UUID attemptId, List<Boolean> correct, Instant at, Cause cause);

    void onboardingCompleted(UUID userId, Instant at, Cause cause);
}
