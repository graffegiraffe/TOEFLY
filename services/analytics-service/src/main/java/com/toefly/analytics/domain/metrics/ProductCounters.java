package com.toefly.analytics.domain.metrics;

import java.time.LocalDate;

/** {@code ProductMetricsDaily} (docs/03 §6.15): counters of one UTC day, without identifiers of learners. */
public record ProductCounters(
        LocalDate day,
        int newUsers,
        int onboarded,
        int plansCreated,
        int firstTasks,
        int attemptsSubmitted,
        int weeksCompleted,
        int weeksCompletedByProgress) {

    public static ProductCounters empty(LocalDate day) {
        return new ProductCounters(day, 0, 0, 0, 0, 0, 0, 0);
    }

    public ProductCounters plus(Counter counter, int delta) {
        return switch (counter) {
            case NEW_USERS ->
                new ProductCounters(
                        day,
                        newUsers + delta,
                        onboarded,
                        plansCreated,
                        firstTasks,
                        attemptsSubmitted,
                        weeksCompleted,
                        weeksCompletedByProgress);
            case ONBOARDED ->
                new ProductCounters(
                        day,
                        newUsers,
                        onboarded + delta,
                        plansCreated,
                        firstTasks,
                        attemptsSubmitted,
                        weeksCompleted,
                        weeksCompletedByProgress);
            case PLANS_CREATED ->
                new ProductCounters(
                        day,
                        newUsers,
                        onboarded,
                        plansCreated + delta,
                        firstTasks,
                        attemptsSubmitted,
                        weeksCompleted,
                        weeksCompletedByProgress);
            case FIRST_TASKS ->
                new ProductCounters(
                        day,
                        newUsers,
                        onboarded,
                        plansCreated,
                        firstTasks + delta,
                        attemptsSubmitted,
                        weeksCompleted,
                        weeksCompletedByProgress);
            case ATTEMPTS_SUBMITTED ->
                new ProductCounters(
                        day,
                        newUsers,
                        onboarded,
                        plansCreated,
                        firstTasks,
                        attemptsSubmitted + delta,
                        weeksCompleted,
                        weeksCompletedByProgress);
            case WEEKS_COMPLETED ->
                new ProductCounters(
                        day,
                        newUsers,
                        onboarded,
                        plansCreated,
                        firstTasks,
                        attemptsSubmitted,
                        weeksCompleted + delta,
                        weeksCompletedByProgress);
            case WEEKS_COMPLETED_BY_PROGRESS ->
                new ProductCounters(
                        day,
                        newUsers,
                        onboarded,
                        plansCreated,
                        firstTasks,
                        attemptsSubmitted,
                        weeksCompleted,
                        weeksCompletedByProgress + delta);
        };
    }

    public enum Counter {
        NEW_USERS,
        ONBOARDED,
        PLANS_CREATED,
        FIRST_TASKS,
        ATTEMPTS_SUBMITTED,
        WEEKS_COMPLETED,
        WEEKS_COMPLETED_BY_PROGRESS
    }
}
