package com.toefly.vocabulary.application.port.out;

import com.toefly.vocabulary.domain.review.DailyLimits;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/** learner_settings: time zone from profile and the daily limit of new cards. */
public interface LearnerSettingsRepository {

    Optional<LearnerSettings> find(UUID userId);

    void saveTimezone(UUID userId, ZoneId zone);

    void saveNewCardsPerDay(UUID userId, int newCardsPerDay);

    void delete(UUID userId);

    record LearnerSettings(ZoneId zone, DailyLimits limits) {}
}
