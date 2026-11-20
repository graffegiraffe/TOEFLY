package com.toefly.analytics.domain.learner;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.analytics.domain.common.Section;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LearnerSettingsTest {

    static final UUID USER = UUID.randomUUID();
    static final Instant T0 = Instant.parse("2026-11-20T21:30:00Z");

    @Test
    @DisplayName("AC-02.02.1: before the onboarding the daily goal is the default 60 minutes in UTC")
    void defaults() {
        LearnerSettings unknown = LearnerSettings.unknown(USER);

        assertThat(unknown.minutesPerDay()).isEqualTo(60);
        assertThat(unknown.timezone()).isEqualTo(ZoneOffset.UTC);
        assertThat(unknown.daysUntilExam(T0)).isNull();
    }

    @Test
    @DisplayName("AC-02.03.3, AC-19.01.4: the learner's day follows the newest time zone")
    void dayInTimezone() {
        LearnerSettings settings = LearnerSettings.unknown(USER)
                .schedule(ZoneId.of("Europe/Minsk"), 30, T0)
                .schedule(ZoneId.of("America/New_York"), 45, T0.minusSeconds(60));

        assertThat(settings.minutesPerDay()).isEqualTo(30);
        assertThat(settings.today(T0)).isEqualTo(LocalDate.of(2026, 11, 21));
    }

    @Test
    @DisplayName("INV-ANL-02: schedule and goal change independently, only by newer events")
    void independentGroups() {
        LearnerSettings settings = LearnerSettings.unknown(USER)
                .goal(LocalDate.of(2026, 12, 27), 5.0, Map.of(Section.READING, 5.5), T0)
                .schedule(ZoneId.of("Europe/Minsk"), 90, T0.plusSeconds(10))
                .goal(LocalDate.of(2027, 1, 1), 4.0, Map.of(), T0.minusSeconds(10))
                .plan(LocalDate.of(2026, 12, 28), LocalDate.of(2026, 11, 22), T0.plusSeconds(20))
                .plan(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 21), T0.minusSeconds(20));

        assertThat(settings.overallTarget()).isEqualTo(5.0);
        assertThat(settings.sectionTargets()).containsEntry(Section.READING, 5.5);
        assertThat(settings.examDate()).isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(settings.minutesPerDay()).isEqualTo(90);
        assertThat(settings.planStart()).isEqualTo(LocalDate.of(2026, 11, 21));
    }

    @Test
    @DisplayName("AC-05.02.1: days until the exam in the learner's calendar, never negative")
    void countdown() {
        LearnerSettings settings = LearnerSettings.unknown(USER)
                .schedule(ZoneId.of("Europe/Minsk"), 30, T0)
                .plan(LocalDate.of(2026, 11, 28), LocalDate.of(2026, 11, 21), T0);

        assertThat(settings.daysUntilExam(T0)).isEqualTo(7);
        assertThat(settings.daysUntilExam(T0.plusSeconds(86_400L * 30))).isZero();
    }
}
