package com.toefly.analytics.adapter.in.web;

import com.toefly.analytics.adapter.in.web.api.DashboardsApi;
import com.toefly.analytics.adapter.in.web.api.StreaksApi;
import com.toefly.analytics.adapter.in.web.dto.Streak;
import com.toefly.analytics.adapter.in.web.dto.StudentDashboard;
import com.toefly.analytics.application.port.in.DashboardQueryUseCase;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Home and Progress of the learner (US-05.02, US-19.01). */
@RestController
public class DashboardController implements DashboardsApi, StreaksApi {

    private final DashboardQueryUseCase dashboards;
    private final CurrentCaller caller;

    public DashboardController(DashboardQueryUseCase dashboards, CurrentCaller caller) {
        this.dashboards = dashboards;
        this.caller = caller;
    }

    @Override
    public ResponseEntity<StudentDashboard> getStudentDashboard(@Nullable UUID studentId) {
        return ResponseEntity.ok(AnalyticsDtoMapper.dashboard(dashboards.student(caller.get(), studentId)));
    }

    @Override
    public ResponseEntity<Streak> getMyStreak() {
        return ResponseEntity.ok(AnalyticsDtoMapper.streak(dashboards.streak(caller.get())));
    }
}
