package com.toefly.analytics.domain.dashboard;

import com.toefly.analytics.domain.common.Section;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One published band estimate, kept for the band chart of Progress (AC-13.02.1). */
public record BandPoint(
        UUID eventId,
        Instant at,
        @Nullable Double overallPoint,
        @Nullable Double overallLow,
        @Nullable Double overallHigh,
        Map<Section, Double> sections) {

    public BandPoint {
        sections = Map.copyOf(sections);
    }
}
