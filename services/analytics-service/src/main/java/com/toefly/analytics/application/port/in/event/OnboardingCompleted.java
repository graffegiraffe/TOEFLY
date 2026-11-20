package com.toefly.analytics.application.port.in.event;

import com.toefly.analytics.domain.common.Section;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@code toefly.profile.onboarding-completed.v1}: the goal, the exam date and the daily minutes. */
public record OnboardingCompleted(
        UUID userId,
        String timezone,
        int minutesPerDay,
        @Nullable LocalDate examDate,
        double overallTarget,
        Map<Section, Double> sectionTargets,
        Instant at)
        implements AnalyticsEvent {

    public OnboardingCompleted {
        sectionTargets = Map.copyOf(sectionTargets);
    }
}
