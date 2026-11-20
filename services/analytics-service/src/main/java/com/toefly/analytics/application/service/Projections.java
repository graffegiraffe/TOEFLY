package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.ProjectionName;
import java.util.List;
import org.springframework.stereotype.Component;

/** The read models in the order events are applied; the learner settings come first because others read them. */
@Component
class Projections {

    private final LearnerProjection learner;
    private final StudentDashboardProjection dashboard;
    private final ActivityProjection activity;
    private final ProductMetricsProjection product;
    private final AiUsageProjection aiUsage;

    Projections(
            LearnerProjection learner,
            StudentDashboardProjection dashboard,
            ActivityProjection activity,
            ProductMetricsProjection product,
            AiUsageProjection aiUsage) {
        this.learner = learner;
        this.dashboard = dashboard;
        this.activity = activity;
        this.product = product;
        this.aiUsage = aiUsage;
    }

    List<Projection> all() {
        return List.of(learner, dashboard, activity, product, aiUsage);
    }

    /** What a rebuild replays: the read model and the settings it reads while it applies events. */
    List<Projection> chain(ProjectionName name) {
        return switch (name) {
            case STUDENT_DASHBOARD -> List.of(dashboard);
            case ACTIVITY -> List.of(learner, activity);
            case PRODUCT_METRICS -> List.of(product);
            case AI_USAGE -> List.of(aiUsage);
        };
    }
}
