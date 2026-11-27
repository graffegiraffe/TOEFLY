package com.toefly.tutoragent.application.port.out;

import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Reads and writes of the agent's tools in planning-, analytics- and content-service (docs/06 §4.5): one hop, answers
 * as JSON objects in maps and lists, the learner's token when there is one.
 */
public interface OwnerToolsPort {

    Map<String, Object> currentWeek(UUID userId, @Nullable String bearer);

    /** Adds an item set to the current week for the agent (addWeekTaskInternal); returns the WeekTask. */
    Map<String, Object> addExtraTask(UUID weekId, Map<String, Object> request, String idempotencyKey);

    Map<String, Object> dashboard(UUID userId, @Nullable String bearer);

    Map<String, Object> timeReport(UUID userId, String period, @Nullable String bearer);

    Map<String, Object> searchItemSets(Map<String, Object> query);

    Map<String, Object> searchGrammarUnits(@Nullable String topic, @Nullable String subcategory, int limit);
}
