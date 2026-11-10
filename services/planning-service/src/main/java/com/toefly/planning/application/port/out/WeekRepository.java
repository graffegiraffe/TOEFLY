package com.toefly.planning.application.port.out;

import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.Week;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface WeekRepository {

    Optional<Week> findById(UUID weekId);

    Optional<Week> findByTask(UUID weekTaskId);

    /** Weeks of the plan by number. */
    List<Week> findByPlan(UUID planId);

    /** Plans with a PUBLISHED or IN_PROGRESS week: the candidates of the nightly check. */
    List<UUID> plansWithOpenWeeks();

    void insert(Week week);

    /**
     * Writes the week and its tasks when its version is unchanged.
     *
     * @throws ConcurrencyConflictException when another writer changed the week first
     */
    int update(Week week);

    /** {@code refId} of every item set, unit and resource ever given to the learner (INV-WK-04). */
    Set<String> assignedRefs(UUID userId);

    /** Item sets the learner did, by type: the sets a repeat may come from. */
    Map<TaskType, List<UUID>> doneItemSets(UUID userId);

    /** Tasks of each type in the weeks from {@code fromWeekNo}. */
    Map<TaskType, Integer> typeCounts(UUID planId, int fromWeekNo);

    void deleteByUser(UUID userId);
}
