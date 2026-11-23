package com.toefly.vocabulary.application.service;

import com.toefly.vocabulary.application.port.out.LearnerSettingsRepository;
import com.toefly.vocabulary.domain.review.DailyLimits;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

/** The learner's zone and limits, and the bounds of the local day around an instant. */
record LearnerDay(ZoneId zone, DailyLimits limits, LocalDate date, Instant start, Instant end) {

    static LearnerDay of(LearnerSettingsRepository settings, UUID userId, Instant now) {
        LearnerSettingsRepository.LearnerSettings found = settings.find(userId)
                .orElse(new LearnerSettingsRepository.LearnerSettings(ZoneOffset.UTC, DailyLimits.defaults()));
        LocalDate date = now.atZone(found.zone()).toLocalDate();
        return new LearnerDay(
                found.zone(),
                found.limits(),
                date,
                date.atStartOfDay(found.zone()).toInstant(),
                date.plusDays(1).atStartOfDay(found.zone()).toInstant());
    }
}
