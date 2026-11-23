package com.toefly.planning.application.port.in;

import com.toefly.planning.domain.week.ExtraTaskDay;
import com.toefly.planning.domain.week.WeekTask;
import java.util.UUID;

/** {@code addWeekTaskInternal} for Toefly Tutor ({@code assignExtraTask}, docs/06-ai-agent.md §4.5). */
public interface AddAgentTaskUseCase {

    /**
     * Adds an approved item set of the local catalog to an open week; the reason becomes the rationale of the task.
     *
     * @throws com.toefly.planning.domain.week.WeekRuleException {@code not-found}, {@code week-not-editable},
     *     {@code extra-task-limit-reached}, {@code item-set-already-in-week}, {@code day-budget-exceeded} or
     *     {@code validation-failed}
     */
    WeekTask add(UUID weekId, UUID itemSetId, ExtraTaskDay day, String reason);
}
