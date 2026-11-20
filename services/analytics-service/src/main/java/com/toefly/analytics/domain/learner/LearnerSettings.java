package com.toefly.analytics.domain.learner;

import com.toefly.analytics.domain.common.Section;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What the projections need to know about a learner: the day boundaries, the daily goal and the exam goal. The schedule
 * ({@code timezone}, {@code minutesPerDay}) and the goal ({@code examDate}, targets) change independently, each only by
 * a newer event (INV-ANL-02), so the order of delivery does not change the result.
 */
public record LearnerSettings(
        UUID userId,
        ZoneId timezone,
        int minutesPerDay,
        @Nullable Instant scheduleAsOf,
        @Nullable LocalDate examDate,
        @Nullable Double overallTarget,
        Map<Section, Double> sectionTargets,
        @Nullable Instant goalAsOf,
        @Nullable LocalDate planStart) {

    /** Daily goal before the learner told us one: the default of the onboarding (AC-02.02.1). */
    public static final int DEFAULT_MINUTES_PER_DAY = 60;

    public LearnerSettings {
        sectionTargets = Map.copyOf(sectionTargets);
    }

    public static LearnerSettings unknown(UUID userId) {
        return new LearnerSettings(
                userId, ZoneOffset.UTC, DEFAULT_MINUTES_PER_DAY, null, null, null, Map.of(), null, null);
    }

    public LearnerSettings schedule(ZoneId zone, int minutes, Instant at) {
        if (scheduleAsOf != null && at.isBefore(scheduleAsOf)) {
            return this;
        }
        return new LearnerSettings(
                userId, zone, minutes, at, examDate, overallTarget, sectionTargets, goalAsOf, planStart);
    }

    public LearnerSettings goal(
            @Nullable LocalDate exam, @Nullable Double overall, Map<Section, Double> sections, Instant at) {
        if (goalAsOf != null && at.isBefore(goalAsOf)) {
            return this;
        }
        return new LearnerSettings(
                userId, timezone, minutesPerDay, scheduleAsOf, exam, overall, sections, at, planStart);
    }

    /**
     * The plan repeats the exam date and starts on {@code start}; it never clears the targets of the onboarding. The
     * earliest plan start is kept, so the time plan of the learner does not count days before it (AC-16.03.1).
     */
    public LearnerSettings plan(@Nullable LocalDate exam, LocalDate start, Instant at) {
        LocalDate first = planStart == null || start.isBefore(planStart) ? start : planStart;
        if (goalAsOf != null && at.isBefore(goalAsOf)) {
            return new LearnerSettings(
                    userId,
                    timezone,
                    minutesPerDay,
                    scheduleAsOf,
                    examDate,
                    overallTarget,
                    sectionTargets,
                    goalAsOf,
                    first);
        }
        return new LearnerSettings(
                userId, timezone, minutesPerDay, scheduleAsOf, exam, overallTarget, sectionTargets, at, first);
    }

    /** The learner's calendar day at {@code now}. */
    public LocalDate today(Instant now) {
        return LocalDate.ofInstant(now, timezone);
    }

    /** Days until the exam, {@code null} without a date; never negative. */
    public @Nullable Integer daysUntilExam(Instant now) {
        if (examDate == null) {
            return null;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(today(now), examDate);
        return (int) Math.max(0, days);
    }
}
