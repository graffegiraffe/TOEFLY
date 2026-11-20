package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.event.AnalyticsEvent;
import com.toefly.analytics.application.port.in.event.AttemptStarted;
import com.toefly.analytics.application.port.in.event.TimeTracked;
import com.toefly.analytics.application.port.in.event.WeekPublished;
import com.toefly.analytics.application.port.in.event.WeekRebalanced;
import com.toefly.analytics.application.port.in.event.WeekTaskCompleted;
import com.toefly.analytics.application.port.out.ActivityRepository;
import com.toefly.analytics.application.port.out.LearnerRepository;
import com.toefly.analytics.domain.activity.ActivityDay;
import com.toefly.analytics.domain.activity.PlannedTask;
import com.toefly.analytics.domain.learner.LearnerSettings;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/**
 * {@code ActivityDay} and {@code StreakView}: time by section, done tasks, the pace against ETS norms and the daily goal.
 * A day enters the streak the moment its goal is met (AC-19.01.2); the norm of an attempt comes from
 * {@code attempt-started}, its time from {@code time-tracked} (both keyed by the learner, so they arrive in order).
 */
@Component
class ActivityProjection implements Projection {

    private final ActivityRepository activity;
    private final LearnerRepository learners;

    ActivityProjection(ActivityRepository activity, LearnerRepository learners) {
        this.activity = activity;
        this.learners = learners;
    }

    @Override
    public void apply(AnalyticsEvent event) {
        switch (event) {
            case AttemptStarted e -> activity.saveAttemptNorm(e.attemptId(), e.userId(), e.estimatedSeconds());
            case TimeTracked e -> {
                Integer norm = activity.attemptNorm(e.attemptId()).orElse(null);
                update(
                        e.userId(),
                        e.localDate(),
                        e.at(),
                        day -> day.timeTracked(
                                e.taskType(), e.weekSection(), e.activeSeconds(), norm, e.suspicious(), e.at()));
            }
            case WeekPublished e -> {
                for (WeekPublished.TaskDay task : e.tasks()) {
                    PlannedTask planned = activity.plannedTask(task.weekTaskId())
                            .map(known -> known.movedTo(task.localDate()))
                            .orElseGet(() -> new PlannedTask(task.weekTaskId(), e.userId(), task.localDate(), null));
                    activity.savePlannedTask(planned);
                }
            }
            case WeekRebalanced e -> {
                for (WeekPublished.TaskDay moved : e.movedTo()) {
                    activity.plannedTask(moved.weekTaskId())
                            .ifPresent(task -> activity.savePlannedTask(task.movedTo(moved.localDate())));
                }
            }
            case WeekTaskCompleted e -> taskDone(e);
            default -> {
                // not part of the activity
            }
        }
    }

    @Override
    public void reset() {
        activity.reset();
    }

    private void taskDone(WeekTaskCompleted e) {
        update(e.userId(), e.localDate(), e.at(), day -> day.taskDone(e.at()));
        PlannedTask task = activity.plannedTask(e.weekTaskId()).orElse(null);
        if (task == null) {
            return;
        }
        PlannedTask done = task.done(e.localDate());
        activity.savePlannedTask(done);
        LocalDate planned = done.plannedDate();
        if (PlannedTask.dayPlanDone(activity.plannedTasks(e.userId(), planned), planned)) {
            update(e.userId(), planned, e.at(), ActivityDay::withDayPlanDone);
        }
    }

    private void update(UUID userId, LocalDate date, Instant at, UnaryOperator<ActivityDay> change) {
        ActivityDay day = activity.findDay(userId, date).orElseGet(() -> ActivityDay.start(userId, date, goal(userId)));
        ActivityDay next = change.apply(day);
        activity.saveDay(next);
        if (next.goalMet() && !day.goalMet()) {
            activity.addStudyDay(userId, date, at);
        }
    }

    private int goal(UUID userId) {
        return learners.find(userId)
                .map(LearnerSettings::minutesPerDay)
                .orElse(LearnerSettings.DEFAULT_MINUTES_PER_DAY);
    }
}
