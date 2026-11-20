package com.toefly.analytics.application.port.in.event;

import com.toefly.analytics.domain.common.Section;
import java.time.Instant;
import java.util.UUID;

/** {@code toefly.practice.ability-updated.v1}: the new θ of a section. */
public record AbilityUpdated(UUID userId, Section section, double theta, double standardError, Instant at)
        implements AnalyticsEvent {}
