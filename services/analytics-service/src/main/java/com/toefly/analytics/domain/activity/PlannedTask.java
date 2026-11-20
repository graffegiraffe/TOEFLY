package com.toefly.analytics.domain.activity;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A task of the published week on its planned day. The plan of a day is done when every task planned for it is done on
 * that day or earlier ("Get ahead"); tasks overdue from earlier days do not count (AC-19.02.1).
 */
public record PlannedTask(
        UUID weekTaskId,
        UUID userId,
        LocalDate plannedDate,
        @Nullable LocalDate doneDate) {

    public PlannedTask done(LocalDate on) {
        if (doneDate != null && !on.isBefore(doneDate)) {
            return this;
        }
        return new PlannedTask(weekTaskId, userId, plannedDate, on);
    }

    public PlannedTask movedTo(LocalDate day) {
        return new PlannedTask(weekTaskId, userId, day, doneDate);
    }

    /** {@code true} when {@code day} has tasks and all of them were done by the end of it. */
    public static boolean dayPlanDone(Collection<PlannedTask> tasks, LocalDate day) {
        boolean any = false;
        for (PlannedTask task : tasks) {
            if (!task.plannedDate().equals(day)) {
                continue;
            }
            any = true;
            if (task.doneDate() == null || task.doneDate().isAfter(day)) {
                return false;
            }
        }
        return any;
    }
}
