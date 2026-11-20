package com.toefly.analytics.application.port.out;

import com.toefly.analytics.domain.activity.ActivityDay;
import com.toefly.analytics.domain.activity.PlannedTask;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code activity_day} and {@code streak_view} with what they are computed from: the ETS norm of each attempt and the
 * planned day of each task.
 */
public interface ActivityRepository {

    Optional<ActivityDay> findDay(UUID userId, LocalDate date);

    void saveDay(ActivityDay day);

    /** Days of {@code [from, to)} in date order. */
    List<ActivityDay> days(UUID userId, LocalDate from, LocalDate to);

    void saveAttemptNorm(UUID attemptId, UUID userId, int normSeconds);

    Optional<Integer> attemptNorm(UUID attemptId);

    Optional<PlannedTask> plannedTask(UUID weekTaskId);

    List<PlannedTask> plannedTasks(UUID userId, LocalDate plannedDate);

    void savePlannedTask(PlannedTask task);

    /** Adds a day with the goal met to {@code streak_view}. */
    void addStudyDay(UUID userId, LocalDate day, Instant at);

    Set<LocalDate> studyDays(UUID userId);

    /** Days with the goal met of every learner since {@code since}. */
    Map<UUID, Set<LocalDate>> studyDaysSince(LocalDate since);

    Optional<Instant> streakAsOf(UUID userId);

    /** Learners with active time in {@code [from, to)}. */
    int activeLearners(LocalDate from, LocalDate to);

    void reset();

    void deleteUser(UUID userId);
}
