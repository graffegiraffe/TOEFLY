package com.toefly.assessment.application.port.in;

import com.toefly.assessment.application.port.out.Cause;
import com.toefly.assessment.domain.estimate.BandEstimate;
import com.toefly.assessment.domain.estimate.Section;
import com.toefly.assessment.domain.estimate.WeeklyEstimate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The band estimate (docs/03-domain-model.md §8) and the signals it is recomputed from. */
public interface BandEstimateUseCase {

    BandEstimate current(Caller caller, @Nullable UUID studentId);

    /** The estimate of a user for an internal call; who may ask is checked by the security chain. */
    BandEstimate ofUser(UUID userId);

    List<WeeklyEstimate> history(Caller caller, @Nullable UUID studentId);

    void abilityUpdated(
            UUID userId, Section section, double theta, double standardError, int n, Instant at, Cause cause);

    void buildSentenceScored(UUID userId, UUID attemptId, List<Boolean> correct, Instant at, Cause cause);

    void onboardingCompleted(UUID userId, Instant at, Cause cause);
}
