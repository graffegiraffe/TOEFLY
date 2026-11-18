package com.toefly.assessment.domain.estimate;

import java.time.LocalDate;

/** Snapshot of the estimate for the graph of the Progress page: the last one of an ISO week (US-13.02). */
public record WeeklyEstimate(LocalDate weekStart, BandEstimate estimate) {}
