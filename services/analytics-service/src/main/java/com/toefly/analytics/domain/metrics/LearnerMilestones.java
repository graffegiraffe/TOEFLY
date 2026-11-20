package com.toefly.analytics.domain.metrics;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Funnel steps of one learner (docs/01 §7.2): each step keeps its earliest day, so redelivery changes nothing. */
public record LearnerMilestones(
        UUID userId,
        @Nullable LocalDate registeredOn,
        @Nullable LocalDate onboardedOn,
        @Nullable LocalDate planCreatedOn,
        @Nullable LocalDate firstTaskOn) {

    public static LearnerMilestones none(UUID userId) {
        return new LearnerMilestones(userId, null, null, null, null);
    }

    public LearnerMilestones registered(LocalDate day) {
        return new LearnerMilestones(userId, earliest(registeredOn, day), onboardedOn, planCreatedOn, firstTaskOn);
    }

    public LearnerMilestones onboarded(LocalDate day) {
        return new LearnerMilestones(userId, registeredOn, earliest(onboardedOn, day), planCreatedOn, firstTaskOn);
    }

    public LearnerMilestones planCreated(LocalDate day) {
        return new LearnerMilestones(userId, registeredOn, onboardedOn, earliest(planCreatedOn, day), firstTaskOn);
    }

    public LearnerMilestones taskDone(LocalDate day) {
        return new LearnerMilestones(userId, registeredOn, onboardedOn, planCreatedOn, earliest(firstTaskOn, day));
    }

    /** {@code true} when this event made a step happen earlier than known, so the daily counter moves. */
    public static boolean moved(@Nullable LocalDate before, @Nullable LocalDate after) {
        return after != null && !after.equals(before);
    }

    private static LocalDate earliest(@Nullable LocalDate known, LocalDate day) {
        return known == null || day.isBefore(known) ? day : known;
    }
}
