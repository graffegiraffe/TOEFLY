package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.event.AnalyticsEvent;
import com.toefly.analytics.application.port.in.event.AttemptSubmitted;
import com.toefly.analytics.application.port.in.event.OnboardingCompleted;
import com.toefly.analytics.application.port.in.event.ProfileCreated;
import com.toefly.analytics.application.port.in.event.StudyPlanCreated;
import com.toefly.analytics.application.port.in.event.WeekCompleted;
import com.toefly.analytics.application.port.in.event.WeekTaskCompleted;
import com.toefly.analytics.application.port.out.ProductMetricsRepository;
import com.toefly.analytics.domain.metrics.LearnerMilestones;
import com.toefly.analytics.domain.metrics.ProductCounters.Counter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * {@code ProductMetricsDaily}: the funnel counts each learner once, on the earliest day of each step; days are UTC.
 * Subscriptions and the diagnostic join when billing-service and mockexam-service publish them.
 */
@Component
class ProductMetricsProjection implements Projection {

    private final ProductMetricsRepository metrics;

    ProductMetricsProjection(ProductMetricsRepository metrics) {
        this.metrics = metrics;
    }

    @Override
    public void apply(AnalyticsEvent event) {
        switch (event) {
            case ProfileCreated e
            when e.student() ->
                step(
                        e.userId(),
                        day(e.at()),
                        Counter.NEW_USERS,
                        LearnerMilestones::registeredOn,
                        m -> m.registered(day(e.at())));
            case OnboardingCompleted e ->
                step(
                        e.userId(),
                        day(e.at()),
                        Counter.ONBOARDED,
                        LearnerMilestones::onboardedOn,
                        m -> m.onboarded(day(e.at())));
            case StudyPlanCreated e ->
                step(
                        e.userId(),
                        day(e.at()),
                        Counter.PLANS_CREATED,
                        LearnerMilestones::planCreatedOn,
                        m -> m.planCreated(day(e.at())));
            case WeekTaskCompleted e ->
                step(
                        e.userId(),
                        e.localDate(),
                        Counter.FIRST_TASKS,
                        LearnerMilestones::firstTaskOn,
                        m -> m.taskDone(e.localDate()));
            case AttemptSubmitted e -> metrics.add(day(e.at()), Counter.ATTEMPTS_SUBMITTED, 1);
            case WeekCompleted e -> {
                metrics.add(day(e.at()), Counter.WEEKS_COMPLETED, 1);
                if (e.closedByProgress()) {
                    metrics.add(day(e.at()), Counter.WEEKS_COMPLETED_BY_PROGRESS, 1);
                }
            }
            default -> {
                // not a product metric
            }
        }
    }

    @Override
    public void reset() {
        metrics.reset();
    }

    /** Moves the counter of a step to its earliest day: a first step adds one, an earlier one moves the count. */
    private void step(
            UUID userId,
            LocalDate day,
            Counter counter,
            Function<LearnerMilestones, @Nullable LocalDate> stepDay,
            UnaryOperator<LearnerMilestones> change) {
        LearnerMilestones known = metrics.milestones(userId).orElseGet(() -> LearnerMilestones.none(userId));
        LearnerMilestones next = change.apply(known);
        LocalDate before = stepDay.apply(known);
        LocalDate after = stepDay.apply(next);
        if (!LearnerMilestones.moved(before, after)) {
            return;
        }
        if (before != null) {
            metrics.add(before, counter, -1);
        }
        metrics.add(day, counter, 1);
        metrics.saveMilestones(next);
    }

    static LocalDate day(Instant at) {
        return LocalDate.ofInstant(at, ZoneOffset.UTC);
    }
}
