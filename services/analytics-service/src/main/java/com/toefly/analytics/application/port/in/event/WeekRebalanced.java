package com.toefly.analytics.application.port.in.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code toefly.planning.week-rebalanced.v1}: tasks moved to other days of the week. */
public record WeekRebalanced(UUID userId, UUID weekId, List<WeekPublished.TaskDay> movedTo, Instant at)
        implements AnalyticsEvent {

    public WeekRebalanced {
        movedTo = List.copyOf(movedTo);
    }
}
