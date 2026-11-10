package com.toefly.practice.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface GetTimeSummaryUseCase {

    /** Active time against the ETS norms by day, week, section or task type (US-16.02). */
    List<TimeBucket> summary(
            Caller caller, @Nullable UUID studentId, LocalDate from, LocalDate to, TimeGrouping grouping);
}
