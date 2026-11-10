package com.toefly.planning.application.port.in;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The goal of {@code toefly.profile.onboarding-completed.v1}. */
public record OnboardingGoal(
        UUID userId,
        int revision,
        @Nullable LocalDate examDate,
        ZoneId timezone,
        int minutesPerDay,
        Map<String, Double> targets) {

    public OnboardingGoal {
        targets = Map.copyOf(targets);
    }
}
