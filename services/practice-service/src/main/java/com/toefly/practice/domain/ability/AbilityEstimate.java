package com.toefly.practice.domain.ability;

import com.toefly.practice.domain.attempt.Section;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Ability of a learner in one section (docs/03-domain-model.md §6.5): theta in logits, accumulated Fisher
 * information and the number of items counted. INV-ABL-01: theta in [-4, 4], information above zero.
 */
public record AbilityEstimate(
        UUID userId,
        Section section,
        double theta,
        double info,
        int n,
        @Nullable Instant updatedAt,
        int version) {

    /** Prior N(0, 1.5^2): information 1 / 1.5^2 (§7.3). */
    public static final double PRIOR_SIGMA = 1.5;

    public static final double PRIOR_INFO = 1 / (PRIOR_SIGMA * PRIOR_SIGMA);

    public AbilityEstimate {
        if (theta < RaschModel.MIN_LOGIT || theta > RaschModel.MAX_LOGIT) {
            throw new IllegalArgumentException("theta out of [-4, 4]: " + theta);
        }
        if (!(info > 0)) {
            throw new IllegalArgumentException("information must be positive: " + info);
        }
    }

    public static AbilityEstimate initial(UUID userId, Section section) {
        return new AbilityEstimate(userId, section, 0, PRIOR_INFO, 0, null, 0);
    }

    public double standardError() {
        return 1 / Math.sqrt(info);
    }
}
