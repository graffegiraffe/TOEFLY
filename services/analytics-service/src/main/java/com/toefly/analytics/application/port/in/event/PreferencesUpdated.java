package com.toefly.analytics.application.port.in.event;

import java.time.Instant;
import java.util.UUID;

/** {@code toefly.profile.preferences-updated.v1}: a new time zone or daily goal. */
public record PreferencesUpdated(UUID userId, String timezone, int minutesPerDay, Instant at)
        implements AnalyticsEvent {}
