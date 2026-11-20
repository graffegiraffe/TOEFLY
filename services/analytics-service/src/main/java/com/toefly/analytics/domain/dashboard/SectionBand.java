package com.toefly.analytics.domain.dashboard;

import com.toefly.analytics.domain.common.Section;
import org.jspecify.annotations.Nullable;

/** Estimate of one section; {@code ready == false} is "Need more data" (AC-13.01.3). */
public record SectionBand(
        Section section,
        boolean ready,
        @Nullable Double point,
        @Nullable Double low,
        @Nullable Double high,
        int n) {}
