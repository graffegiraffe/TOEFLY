package com.toefly.analytics.domain.activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlannedTaskTest {

    static final UUID USER = UUID.randomUUID();
    static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    @Test
    @DisplayName("AC-19.02.1: the plan of a day is done when its tasks are done that day or earlier")
    void dayPlanDone() {
        PlannedTask first = task(DAY).done(DAY);
        PlannedTask ahead = task(DAY).done(DAY.minusDays(1));
        PlannedTask open = task(DAY);

        assertThat(PlannedTask.dayPlanDone(List.of(first, ahead), DAY)).isTrue();
        assertThat(PlannedTask.dayPlanDone(List.of(first, open), DAY)).isFalse();
    }

    @Test
    @DisplayName("AC-19.02.1: overdue tasks of earlier days do not count; a task done late does not close its day")
    void overdueExcluded() {
        PlannedTask overdue = task(DAY.minusDays(1));
        PlannedTask today = task(DAY).done(DAY);
        PlannedTask late = task(DAY.minusDays(2)).done(DAY);

        assertThat(PlannedTask.dayPlanDone(List.of(overdue, today), DAY)).isTrue();
        assertThat(PlannedTask.dayPlanDone(List.of(late), DAY.minusDays(2))).isFalse();
        assertThat(PlannedTask.dayPlanDone(List.of(), DAY)).isFalse();
    }

    @Test
    @DisplayName("docs/03 §6.15: a redelivered completion keeps the earliest day; a rebalance moves the plan")
    void earliestDoneAndMove() {
        PlannedTask done = task(DAY).done(DAY.minusDays(1)).done(DAY);

        assertThat(done.doneDate()).isEqualTo(DAY.minusDays(1));
        assertThat(done.movedTo(DAY.plusDays(2)).plannedDate()).isEqualTo(DAY.plusDays(2));
    }

    private static PlannedTask task(LocalDate planned) {
        return new PlannedTask(UUID.randomUUID(), USER, planned, null);
    }
}
