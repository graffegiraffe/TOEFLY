package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.ForgetLearnerUseCase;
import com.toefly.analytics.application.port.in.RecordEventUseCase;
import com.toefly.analytics.application.port.in.event.AnalyticsEvent;
import com.toefly.analytics.application.port.in.event.EventMeta;
import com.toefly.analytics.application.port.out.ActivityRepository;
import com.toefly.analytics.application.port.out.DashboardRepository;
import com.toefly.analytics.application.port.out.EventJournalRepository;
import com.toefly.analytics.application.port.out.LearnerRepository;
import com.toefly.analytics.application.port.out.ProductMetricsRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The write side of the read models: journal first, then every projection, in one transaction (docs/04 §6.7). */
@Service
public class EventRecorder implements RecordEventUseCase, ForgetLearnerUseCase {

    private final EventJournalRepository journal;
    private final Projections projections;
    private final LearnerRepository learners;
    private final DashboardRepository dashboards;
    private final ActivityRepository activity;
    private final ProductMetricsRepository metrics;

    public EventRecorder(
            EventJournalRepository journal,
            Projections projections,
            LearnerRepository learners,
            DashboardRepository dashboards,
            ActivityRepository activity,
            ProductMetricsRepository metrics) {
        this.journal = journal;
        this.projections = projections;
        this.learners = learners;
        this.dashboards = dashboards;
        this.activity = activity;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public boolean record(EventMeta meta, AnalyticsEvent event) {
        if (!journal.append(meta, event)) {
            return false;
        }
        for (Projection projection : projections.all()) {
            projection.apply(event);
        }
        return true;
    }

    @Override
    @Transactional
    public void forget(UUID userId) {
        journal.deleteUser(userId);
        learners.deleteUser(userId);
        dashboards.deleteUser(userId);
        activity.deleteUser(userId);
        metrics.deleteUser(userId);
    }
}
