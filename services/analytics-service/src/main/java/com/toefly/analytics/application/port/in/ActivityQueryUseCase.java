package com.toefly.analytics.application.port.in;

import com.toefly.analytics.domain.activity.ActivityDay;
import com.toefly.analytics.domain.activity.TimeReport;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Activity by day and time against ETS norms (US-16.02, US-16.03). */
public interface ActivityQueryUseCase {

    /** Longest period one request may cover. */
    int MAX_DAYS = 400;

    List<ActivityDay> days(Caller caller, @Nullable UUID studentId, LocalDate from, LocalDate to);

    TimeReport timeReport(Caller caller, @Nullable UUID studentId, @Nullable LocalDate from, @Nullable LocalDate to);

    /**
     * The time report of a user over a named period in the learner's time zone, for an internal call; who may ask is
     * checked by the security chain.
     */
    TimeReport timeReportOf(UUID userId, ReportPeriod period);
}
