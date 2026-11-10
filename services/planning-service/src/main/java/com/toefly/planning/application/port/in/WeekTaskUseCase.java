package com.toefly.planning.application.port.in;

import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekTask;
import java.util.UUID;

/** Commands of the learner on the tasks of a week. */
public interface WeekTaskUseCase {

    WeekTask complete(UUID userId, UUID taskId);

    WeekTask uncomplete(UUID userId, UUID taskId);

    WeekTask skip(UUID userId, UUID taskId);

    /** Moves a task to another day; {@code ifMatch} is the version of the week. */
    WeekTask move(UUID userId, UUID weekId, UUID taskId, int ifMatch, int dayIndex);

    Week rebalance(UUID userId, UUID weekId);

    Week finishEarly(UUID userId, UUID weekId);
}
