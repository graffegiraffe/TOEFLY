package com.toefly.planning.application.service;

import com.toefly.planning.application.port.in.VersionMismatchException;
import com.toefly.planning.application.port.in.WeekTaskUseCase;
import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.application.port.out.PlanningEventPublisherPort;
import com.toefly.planning.application.port.out.StudyPlanRepository;
import com.toefly.planning.application.port.out.WeekRepository;
import com.toefly.planning.domain.plan.LearnerCalendar;
import com.toefly.planning.domain.plan.StudyPlan;
import com.toefly.planning.domain.week.Rebalance;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekRuleException;
import com.toefly.planning.domain.week.WeekTask;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commands of the learner on the tasks of a week; another learner's week looks missing (NFR-SEC-02). */
@Service
@Transactional
public class WeekTaskService implements WeekTaskUseCase {

    private final WeekRepository weeks;
    private final StudyPlanRepository plans;
    private final PlanningEventPublisherPort events;
    private final WeekLifecycle lifecycle;
    private final Clock clock;

    public WeekTaskService(
            WeekRepository weeks,
            StudyPlanRepository plans,
            PlanningEventPublisherPort events,
            WeekLifecycle lifecycle,
            Clock clock) {
        this.weeks = weeks;
        this.plans = plans;
        this.events = events;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    @Override
    public WeekTask complete(UUID userId, UUID taskId) {
        Week week = byTask(userId, taskId);
        Instant now = clock.instant();
        LocalDate today = today(week, now);
        week.completeManually(taskId, today, now).ifPresent(task -> {
            weeks.update(week);
            events.weekTaskCompleted(week, task, today, Cause.none());
        });
        return week.task(taskId);
    }

    @Override
    public WeekTask uncomplete(UUID userId, UUID taskId) {
        Week week = byTask(userId, taskId);
        WeekTask task = week.uncomplete(taskId);
        weeks.update(week);
        return task;
    }

    @Override
    public WeekTask skip(UUID userId, UUID taskId) {
        Week week = byTask(userId, taskId);
        WeekTask task = week.skip(taskId);
        weeks.update(week);
        return task;
    }

    @Override
    public WeekTask move(UUID userId, UUID weekId, UUID taskId, int ifMatch, int dayIndex) {
        Week week = byId(userId, weekId);
        if (week.version() != ifMatch) {
            throw new VersionMismatchException(week.version());
        }
        WeekTask task = week.move(taskId, dayIndex);
        weeks.update(week);
        return task;
    }

    @Override
    public Week rebalance(UUID userId, UUID weekId) {
        Week week = byId(userId, weekId);
        Instant now = clock.instant();
        week.rebalance(today(week, now), now, true).ifPresent(rebalance -> publish(week, rebalance, now));
        weeks.update(week);
        return week;
    }

    private void publish(Week week, Rebalance rebalance, Instant now) {
        if (!rebalance.skipped().isEmpty()) {
            events.weekRebalanced(week, rebalance, now, Cause.none());
        }
    }

    @Override
    public Week finishEarly(UUID userId, UUID weekId) {
        Week week = byId(userId, weekId);
        Instant now = clock.instant();
        StudyPlan plan = plan(week);
        week.finishEarly(LearnerCalendar.today(now, plan.timezone()), now);
        lifecycle.closed(plan, week, now, Cause.none());
        return week;
    }

    private Week byTask(UUID userId, UUID taskId) {
        return weeks.findByTask(taskId)
                .filter(week -> week.userId().equals(userId))
                .orElseThrow(() -> new WeekRuleException("not-found", "No task " + taskId));
    }

    private Week byId(UUID userId, UUID weekId) {
        return weeks.findById(weekId)
                .filter(week -> week.userId().equals(userId) && week.status().visibleToStudent())
                .orElseThrow(() -> new WeekRuleException("not-found", "No week " + weekId));
    }

    private StudyPlan plan(Week week) {
        return plans.findById(week.planId()).orElseThrow();
    }

    private LocalDate today(Week week, Instant now) {
        return LearnerCalendar.today(now, plan(week).timezone());
    }
}
