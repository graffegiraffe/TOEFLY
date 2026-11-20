package com.toefly.analytics.domain.dashboard;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The latest band estimate of assessment-service; the overall range exists only when every section has one. */
public record BandSnapshot(
        @Nullable Double overallPoint,
        @Nullable Double overallLow,
        @Nullable Double overallHigh,
        List<SectionBand> sections,
        Instant asOf) {

    public BandSnapshot {
        sections = List.copyOf(sections);
    }
}
