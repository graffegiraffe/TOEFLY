package com.toefly.analytics.adapter.in.web;

import com.toefly.analytics.adapter.in.web.api.InternalAnalyticsApi;
import com.toefly.analytics.adapter.in.web.dto.ReportPeriod;
import com.toefly.analytics.adapter.in.web.dto.StudentDashboard;
import com.toefly.analytics.adapter.in.web.dto.TimeReport;
import com.toefly.analytics.application.port.in.ActivityQueryUseCase;
import com.toefly.analytics.application.port.in.DashboardQueryUseCase;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /internal/v1}: the dashboard and the time report for the tools of tutor-agent-service, with the service
 * token or the user's own JWT (docs/05-api-contracts.md §9.3, docs/06-ai-agent.md §4.5).
 */
@RestController
public class InternalAnalyticsController implements InternalAnalyticsApi {

    private final DashboardQueryUseCase dashboards;
    private final ActivityQueryUseCase activity;

    public InternalAnalyticsController(DashboardQueryUseCase dashboards, ActivityQueryUseCase activity) {
        this.dashboards = dashboards;
        this.activity = activity;
    }

    @Override
    public ResponseEntity<StudentDashboard> getStudentDashboardInternal(UUID userId) {
        return ResponseEntity.ok(AnalyticsDtoMapper.dashboard(dashboards.studentOf(userId)));
    }

    @Override
    public ResponseEntity<TimeReport> getTimeReportInternal(UUID userId, @Nullable ReportPeriod period) {
        var named = period == null
                ? com.toefly.analytics.application.port.in.ReportPeriod.THIS_WEEK
                : com.toefly.analytics.application.port.in.ReportPeriod.valueOf(period.name());
        return ResponseEntity.ok(AnalyticsDtoMapper.timeReport(activity.timeReportOf(userId, named)));
    }
}
