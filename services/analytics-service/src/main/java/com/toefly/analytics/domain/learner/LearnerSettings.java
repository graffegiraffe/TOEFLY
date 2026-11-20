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
        @Nullable Instant goalAsOf) {

    /** Daily goal before the learner told us one: the default of the onboarding (AC-02.02.1). */
    public static final int DEFAULT_MINUTES_PER_DAY = 60;

    public LearnerSettings {
        sectionTargets = Map.copyOf(sectionTargets);
    }

    public static LearnerSettings unknown(UUID userId) {
        return new LearnerSettings(userId, ZoneOffset.UTC, DEFAULT_MINUTES_PER_DAY, null, null, null, Map.of(), null);
    }

    public LearnerSettings schedule(ZoneId zone, int minutes, Instant at) {
        if (scheduleAsOf != null && at.isBefore(scheduleAsOf)) {
            return this;
        }
        return new LearnerSettings(userId, zone, minutes, at, examDate, overallTarget, sectionTargets, goalAsOf);
    }

    public LearnerSettings goal(
            @Nullable LocalDate exam, @Nullable Double overall, Map<Section, Double> sections, Instant at) {
        if (goalAsOf != null && at.isBefore(goalAsOf)) {
            return this;
        }
        return new LearnerSettings(userId, timezone, minutesPerDay, scheduleAsOf, exam, overall, sections, at);
    }

    /** The plan repeats the exam date; it never clears the targets of the onboarding. */
    public LearnerSettings examDate(@Nullable LocalDate exam, Instant at) {
        if (goalAsOf != null && at.isBefore(goalAsOf)) {
            return this;
        }
        return new LearnerSettings(
                userId, timezone, minutesPerDay, scheduleAsOf, exam, overallTarget, sectionTargets, at);
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
