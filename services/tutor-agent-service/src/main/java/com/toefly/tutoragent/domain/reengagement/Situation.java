package com.toefly.tutoragent.domain.reengagement;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Why a come-back message is written and how often (docs/06 §5.10). */
public enum Situation {
    AWAY_2_DAYS,
    AWAY_7_DAYS,
    WEEK_BEHIND,
    EXAM_SOON_INACTIVE;

    /**
     * The situation of a learner at the local morning check and the key of its episode: one message per episode of
     * inactivity, per week behind, or per 3 days before the exam.
     */
    public static Optional<Episode> of(
            Instant lastActive,
            @Nullable LocalDate examDate,
            @Nullable Instant rebalancedAt,
            int weekNo,
            ZoneId zone,
            Instant now) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate lastDay = LocalDate.ofInstant(lastActive, zone);
        long idleDays = ChronoUnit.DAYS.between(lastDay, today) - 1;
        String episode = lastDay.toString();
        if (examDate != null
                && idleDays >= 2
                && !examDate.isBefore(today)
                && ChronoUnit.DAYS.between(today, examDate) <= 14) {
            return Optional.of(new Episode(EXAM_SOON_INACTIVE, today.toEpochDay() / 3 + ""));
        }
        if (idleDays >= 7) {
            return Optional.of(new Episode(AWAY_7_DAYS, episode));
        }
        if (idleDays >= 2) {
            return Optional.of(new Episode(AWAY_2_DAYS, episode));
        }
        if (rebalancedAt != null && Duration.between(rebalancedAt, now).compareTo(Duration.ofDays(1)) <= 0) {
            return Optional.of(new Episode(WEEK_BEHIND, "week-" + weekNo));
        }
        return Optional.empty();
    }

    public record Episode(Situation situation, String key) {}
}
