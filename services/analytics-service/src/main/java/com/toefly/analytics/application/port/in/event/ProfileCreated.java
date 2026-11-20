package com.toefly.analytics.application.port.in.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code toefly.profile.profile-created.v1}: a registration; learners enter the funnel. */
public record ProfileCreated(UUID userId, List<String> roles, Instant at) implements AnalyticsEvent {

    public ProfileCreated {
        roles = List.copyOf(roles);
    }

    public boolean student() {
        return roles.contains("STUDENT");
    }
}
