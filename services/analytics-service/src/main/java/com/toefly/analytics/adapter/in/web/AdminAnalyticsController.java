package com.toefly.analytics.adapter.in.web;

import com.toefly.analytics.adapter.in.web.api.AdminMetricsApi;
import com.toefly.analytics.adapter.in.web.api.ProjectionsApi;
import com.toefly.analytics.adapter.in.web.dto.AiUsageReport;
import com.toefly.analytics.adapter.in.web.dto.ProductMetrics;
import com.toefly.analytics.adapter.in.web.dto.ProjectionName;
import com.toefly.analytics.adapter.in.web.dto.ProjectionRebuildJob;
import com.toefly.analytics.application.port.in.AdminMetricsUseCase;
import com.toefly.analytics.application.port.in.RebuildProjectionUseCase;
import com.toefly.analytics.application.port.in.RebuildStart;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** Product metrics, AI spend and rebuilds of the read models; admins only. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController implements AdminMetricsApi, ProjectionsApi {

    private final AdminMetricsUseCase metrics;
    private final RebuildProjectionUseCase rebuilds;
    private final CurrentCaller caller;

    public AdminAnalyticsController(
            AdminMetricsUseCase metrics, RebuildProjectionUseCase rebuilds, CurrentCaller caller) {
        this.metrics = metrics;
        this.rebuilds = rebuilds;
        this.caller = caller;
    }

    @Override
    public ResponseEntity<ProductMetrics> getProductMetrics(@Nullable LocalDate from, @Nullable LocalDate to) {
        return ResponseEntity.ok(AnalyticsDtoMapper.product(metrics.product(from, to)));
    }

    @Override
    public ResponseEntity<AiUsageReport> getAiUsageReport(@Nullable LocalDate from, @Nullable LocalDate to) {
        return ResponseEntity.ok(AnalyticsDtoMapper.aiUsage(metrics.aiUsage(from, to)));
    }

    @Override
    public ResponseEntity<ProjectionRebuildJob> rebuildProjection(ProjectionName projection, UUID idempotencyKey) {
        RebuildStart start = rebuilds.start(
                com.toefly.analytics.application.port.in.ProjectionName.valueOf(projection.name()),
                caller.get().userId(),
                idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create(
                        "/api/v1/admin/projections/rebuilds/" + start.job().id()));
        if (start.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(AnalyticsDtoMapper.job(start.job()));
    }

    @Override
    public ResponseEntity<ProjectionRebuildJob> getProjectionRebuild(UUID jobId) {
        return ResponseEntity.ok(AnalyticsDtoMapper.job(rebuilds.get(jobId)));
    }
}
