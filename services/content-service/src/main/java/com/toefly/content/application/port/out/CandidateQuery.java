package com.toefly.content.application.port.out;

import com.toefly.content.domain.catalog.RelativeDifficulty;
import com.toefly.content.domain.catalog.TaskType;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What the catalog filters for extra practice: approved TOEFL 2026 sets of a task type without the tag
 * {@value #EXAM_RESERVED}, optionally with a skill tag or item subskill, a topic text and a band of b.
 *
 * @param skill a skill tag in lower case ({@code reading.inference})
 * @param subskill an item subskill in upper case ({@code INFERENCE})
 */
public record CandidateQuery(
        TaskType taskType,
        @Nullable String skill,
        @Nullable String subskill,
        @Nullable String topic,
        RelativeDifficulty.@Nullable Band band,
        List<UUID> excludeItemSetIds,
        int limit) {

    /** Tag of the sets that mockexam-service keeps for exam forms (docs/06-ai-agent.md §4.5). */
    public static final String EXAM_RESERVED = "exam-reserved";

    public CandidateQuery {
        excludeItemSetIds = List.copyOf(excludeItemSetIds);
    }
}
