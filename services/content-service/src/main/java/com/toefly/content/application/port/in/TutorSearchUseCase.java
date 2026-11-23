package com.toefly.content.application.port.in;

import com.toefly.content.application.port.in.BrowseCatalogUseCase.Row;
import com.toefly.content.application.port.in.BrowseUnitsUseCase.UnitView;
import com.toefly.content.domain.catalog.GrammarUnitCode;
import com.toefly.content.domain.catalog.RelativeDifficulty;
import com.toefly.content.domain.catalog.TaskType;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Searches for the tools of tutor-agent-service (docs/06-ai-agent.md §4.5, docs/12 phase 5c decision 2): extra
 * practice sets by task type, skill, topic and difficulty, and grammar units by mistake subcategory or topic. Both
 * read the catalog only; there is no vector search.
 */
public interface TutorSearchUseCase {

    int MAX_SETS = 5;
    int DEFAULT_SETS = 3;
    int MAX_UNITS = 10;
    int DEFAULT_UNITS = 3;

    /**
     * @param skillFocus a skill tag ({@code reading.inference}) or a mistake subcategory ({@code READING.INFERENCE});
     *     matches the skills of the set or the subskill of one of its items
     * @param referenceB the difficulty b that {@code difficulty} is relative to; without it difficulty is ignored
     */
    record ItemSetQuery(
            TaskType taskType,
            @Nullable String skillFocus,
            @Nullable String topic,
            RelativeDifficulty difficulty,
            @Nullable Double referenceB,
            List<UUID> excludeItemSetIds,
            @Nullable Integer limit) {

        public ItemSetQuery {
            excludeItemSetIds = List.copyOf(excludeItemSetIds);
        }
    }

    /** A grammar unit found by the search with its taxonomy code and the title of its source. */
    record UnitMatch(UnitView unit, GrammarUnitCode code, String sourceTitle) {}

    /** Approved TOEFL 2026 sets, never reserved for mock exams, closest to the reference difficulty first. */
    List<Row> searchItemSets(ItemSetQuery query);

    /** Approved grammar units: the unit of the subcategory first, then units whose title matches the topic. */
    List<UnitMatch> searchGrammarUnits(@Nullable String topic, @Nullable String subcategory, @Nullable Integer limit);
}
