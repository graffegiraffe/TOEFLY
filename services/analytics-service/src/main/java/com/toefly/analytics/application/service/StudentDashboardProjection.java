package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.event.AbilityUpdated;
import com.toefly.analytics.application.port.in.event.AnalyticsEvent;
import com.toefly.analytics.application.port.in.event.AttemptScored;
import com.toefly.analytics.application.port.in.event.BandEstimateUpdated;
import com.toefly.analytics.application.port.in.event.SubmissionEvaluated;
import com.toefly.analytics.application.port.in.event.WeekCompleted;
import com.toefly.analytics.application.port.in.event.WeekPublished;
import com.toefly.analytics.application.port.in.event.WeekTaskCompleted;
import com.toefly.analytics.application.port.out.DashboardRepository;
import com.toefly.analytics.domain.common.Section;
import com.toefly.analytics.domain.dashboard.BandPoint;
import com.toefly.analytics.domain.dashboard.BandSnapshot;
import com.toefly.analytics.domain.dashboard.RecentResult;
import com.toefly.analytics.domain.dashboard.SectionBand;
import com.toefly.analytics.domain.dashboard.StudentDashboard;
import com.toefly.analytics.domain.dashboard.WeekSummary;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/** {@code StudentDashboardView} with the weeks and band points behind the Progress charts (docs/04 §6.7). */
@Component
class StudentDashboardProjection implements Projection {

    private final DashboardRepository dashboards;

    StudentDashboardProjection(DashboardRepository dashboards) {
        this.dashboards = dashboards;
    }

    @Override
    public void apply(AnalyticsEvent event) {
        switch (event) {
            case WeekPublished e -> {
                update(
                        e.userId(),
                        d -> d.weekPublished(
                                e.weekId(), e.weekNo(), e.day1(), e.tasks().size(), e.at()));
                dashboards.saveWeek(dashboards
                        .findWeek(e.weekId())
                        .map(w -> w.published(e.day1(), e.tasks().size()))
                        .orElseGet(() -> WeekSummary.published(
                                e.weekId(),
                                e.userId(),
                                e.weekNo(),
                                e.day1(),
                                e.tasks().size(),
                                e.at())));
            }
            case WeekTaskCompleted e -> {
                update(e.userId(), d -> d.weekProgress(e.weekId(), e.weekNo(), e.progressPercent(), e.at()));
                WeekSummary week = dashboards
                        .findWeek(e.weekId())
                        .orElseGet(() -> new WeekSummary(
                                e.weekId(), e.userId(), e.weekNo(), null, null, 0, 0, 0, false, null, e.at()));
                dashboards.saveWeek(week.taskDone(e.progressPercent(), e.at()));
            }
            case WeekCompleted e -> {
                update(
                        e.userId(),
                        d -> d.weekCompleted(
                                e.weekId(), e.weekNo(), e.startDate(), e.tasksTotal(), e.progressPercent(), e.at()));
                WeekSummary week = dashboards
                        .findWeek(e.weekId())
                        .orElseGet(() -> WeekSummary.published(
                                e.weekId(), e.userId(), e.weekNo(), e.startDate(), e.tasksTotal(), e.at()));
                dashboards.saveWeek(week.completed(
                        e.startDate(),
                        e.endDate(),
                        e.progressPercent(),
                        e.tasksTotal(),
                        e.tasksDone(),
                        e.closedBy(),
                        e.at()));
            }
            case BandEstimateUpdated e -> {
                update(
                        e.userId(),
                        d -> d.band(new BandSnapshot(
                                e.overallPoint(), e.overallLow(), e.overallHigh(), e.sections(), e.at())));
                Map<Section, Double> points = new EnumMap<>(Section.class);
                for (SectionBand band : e.sections()) {
                    if (band.ready() && band.point() != null) {
                        points.put(band.section(), band.point());
                    }
                }
                dashboards.addBandPoint(
                        e.userId(),
                        new BandPoint(
                                bandPointId(e), e.at(), e.overallPoint(), e.overallLow(), e.overallHigh(), points));
            }
            case AbilityUpdated e ->
                update(e.userId(), d -> d.ability(e.section(), e.theta(), e.standardError(), e.at()));
            case AttemptScored e ->
                update(
                        e.userId(),
                        d -> d.result(RecentResult.attempt(
                                e.attemptId(), e.taskType(), e.section(), e.correctCount(), e.totalCount(), e.at())));
            case SubmissionEvaluated e ->
                update(
                        e.userId(),
                        d -> d.result(RecentResult.submission(
                                e.submissionId(), e.taskType(), e.section(), e.rubricScore(), e.at())));
            default -> {
                // not part of the dashboard
            }
        }
    }

    @Override
    public void reset() {
        dashboards.reset();
    }

    private void update(UUID userId, UnaryOperator<StudentDashboard> change) {
        StudentDashboard dashboard = dashboards.find(userId).orElseGet(() -> StudentDashboard.empty(userId));
        dashboards.save(change.apply(dashboard));
    }

    /** The point of one estimate is identified by the learner and its moment, so a replay writes the same row. */
    private static UUID bandPointId(BandEstimateUpdated e) {
        return UUID.nameUUIDFromBytes(
                (e.userId() + "/" + e.at().toEpochMilli()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
