package com.toefly.planning.application.service;

import com.toefly.planning.application.port.in.TrackAttemptUseCase;
import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.application.port.out.LearnerSignalPort;
import com.toefly.planning.application.port.out.PlanningEventPublisherPort;
import com.toefly.planning.application.port.out.StudyPlanRepository;
import com.toefly.planning.application.port.out.WeekRepository;
import com.toefly.planning.domain.plan.LearnerCalendar;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.Week;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code attempt-started} opens the task and the week, {@code attempt-scored} closes the task once and publishes
 * {@code week-task-completed} (AC-04.03.3, docs/03 §10.8). A repeat of either event changes nothing.
 */
@Service
@Transactional
public class AttemptTrackingService implements TrackAttemptUseCase {

    private final WeekRepository weeks;
    private final StudyPlanRepository plans;
    private final LearnerSignalPort signals;
    private final PlanningEventPublisherPort events;
    private final Clock clock;

    public AttemptTrackingService(
            WeekRepository weeks,
            StudyPlanRepository plans,
            LearnerSignalPort signals,
            PlanningEventPublisherPort events,
            Clock clock) {
        this.weeks = weeks;
        this.plans = plans;
        this.signals = signals;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public void attemptStarted(
            UUID userId, @Nullable UUID weekTaskId, UUID attemptId, boolean retry, Instant startedAt) {
        ownWeek(userId, weekTaskId).ifPresent(week -> {
            if (week.start(weekTaskId, attemptId, clock.instant())) {
                weeks.update(week);
            }
        });
    }

    @Override
    public void attemptScored(
            UUID userId,
            @Nullable UUID weekTaskId,
            UUID attemptId,
            TaskType taskType,
            int correct,
            int total,
            boolean practice,
            Instant scoredAt,
            Cause cause) {
        if (practice) {
            signals.recordResult(userId, attemptId, taskType, correct, total, scoredAt);
        }
        Optional<Week> owned = ownWeek(userId, weekTaskId);
        if (owned.isEmpty()) {
            return;
        }
        Week week = owned.get();
        Instant now = clock.instant();
        LocalDate today = plans.findById(week.planId())
                .map(plan -> LearnerCalendar.today(now, plan.timezone()))
                .orElseThrow();
        week.complete(weekTaskId, attemptId, today, now).ifPresent(task -> {
            weeks.update(week);
            events.weekTaskCompleted(week, task, today, cause);
        });
    }

    private Optional<Week> ownWeek(UUID userId, @Nullable UUID weekTaskId) {
        if (weekTaskId == null) {
            return Optional.empty();
        }
        return weeks.findByTask(weekTaskId).filter(week -> week.userId().equals(userId));
    }
}
