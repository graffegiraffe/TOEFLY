package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.AdminMetricsUseCase;
import com.toefly.analytics.application.port.out.ActivityRepository;
import com.toefly.analytics.application.port.out.AiUsageRepository;
import com.toefly.analytics.application.port.out.ProductMetricsRepository;
import com.toefly.analytics.domain.metrics.AiUsageReport;
import com.toefly.analytics.domain.metrics.ProductReport;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Product metrics and AI spend; days are UTC, the default period is the last 30 days. */
@Service
public class AdminMetricsService implements AdminMetricsUseCase {

    static final int DEFAULT_DAYS = 30;

    private final ProductMetricsRepository metrics;
    private final ActivityRepository activity;
    private final AiUsageRepository usage;
    private final Clock clock;

    public AdminMetricsService(
            ProductMetricsRepository metrics, ActivityRepository activity, AiUsageRepository usage, Clock clock) {
        this.metrics = metrics;
        this.activity = activity;
        this.usage = usage;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductReport product(@Nullable LocalDate from, @Nullable LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate end = to == null ? today.plusDays(1) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS) : from;
        ActivityQueryService.check(start, end);
        LocalDate sdrStart = today.minusDays(ProductReport.SDR_WINDOW_DAYS);
        LocalDate since = start.isBefore(sdrStart) ? start : sdrStart;
        return ProductReport.of(
                start,
                end,
                today,
                metrics.counters(start, end),
                metrics.allMilestones(),
                activity.studyDaysSince(since));
    }

    @Override
    @Transactional(readOnly = true)
    public AiUsageReport aiUsage(@Nullable LocalDate from, @Nullable LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate end = to == null ? today.plusDays(1) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS) : from;
        ActivityQueryService.check(start, end);
        return AiUsageReport.of(start, end, usage.rows(start, end), activity.activeLearners(start, end));
    }
}
