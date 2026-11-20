package com.toefly.analytics.application.port.in.event;

import com.toefly.analytics.domain.dashboard.SectionBand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@code toefly.assessment.band-estimate-updated.v1}: the recalculated estimate. */
public record BandEstimateUpdated(
        UUID userId,
        @Nullable Double overallPoint,
        @Nullable Double overallLow,
        @Nullable Double overallHigh,
        List<SectionBand> sections,
        Instant at)
        implements AnalyticsEvent {

    public BandEstimateUpdated {
        sections = List.copyOf(sections);
    }
}
