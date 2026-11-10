package com.toefly.planning.application.port.out;

import com.toefly.planning.domain.plan.StudyPlan;
import com.toefly.planning.domain.week.Rebalance;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekTask;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Domain events of planning-service, written to the outbox in the transaction of the change. */
public interface PlanningEventPublisherPort {

    List<String> topics();

    void studyPlanCreated(StudyPlan plan, Cause cause);

    /** {@code week-draft-generated} and {@code week-published} of a week without a tutor (AC-04.07.1). */
    void weekPublished(Week week, Cause cause);

    void weekTaskCompleted(Week week, WeekTask task, LocalDate localDate, Cause cause);

    void weekRebalanced(Week week, Rebalance rebalance, Instant occurredAt, Cause cause);

    void weekCompleted(Week week, Cause cause);
}
