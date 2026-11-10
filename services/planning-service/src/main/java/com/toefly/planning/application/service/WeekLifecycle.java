package com.toefly.planning.application.service;

import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.application.port.out.PlanningEventPublisherPort;
import com.toefly.planning.application.port.out.StudyPlanRepository;
import com.toefly.planning.application.port.out.WeekRepository;
import com.toefly.planning.domain.plan.StudyPlan;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** What follows the close of a week for a learner without a tutor: the next week at once (AC-04.07.1, 04.07.4). */
@Component
class WeekLifecycle {

    private final StudyPlanRepository plans;
    private final WeekRepository weeks;
    private final WeekPlanner planner;
    private final PlanningEventPublisherPort events;

    WeekLifecycle(
            StudyPlanRepository plans, WeekRepository weeks, WeekPlanner planner, PlanningEventPublisherPort events) {
        this.plans = plans;
        this.weeks = weeks;
        this.planner = planner;
        this.events = events;
    }

    /**
     * Saves the closed week, publishes {@code week-completed} and composes the next week from the day after the
     * close; empty when the plan is over.
     */
    Optional<Week> closed(StudyPlan plan, Week week, Instant now, Cause cause) {
        if (week.status() != WeekStatus.COMPLETED) {
            throw new IllegalStateException("Week " + week.weekNo() + " is not closed");
        }
        weeks.update(week);
        events.weekCompleted(week, cause);
        var day1 = Objects.requireNonNull(week.closedOn()).plusDays(1);
        int next = plan.advance(week.weekNo(), day1);
        plans.update(plan);
        if (next == 0) {
            return Optional.empty();
        }
        Week composed = planner.compose(plan, next, day1, now);
        weeks.insert(composed);
        events.weekPublished(composed, cause);
        return Optional.of(composed);
    }
}
