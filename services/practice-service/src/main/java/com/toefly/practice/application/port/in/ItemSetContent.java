package com.toefly.practice.application.port.in;

import com.toefly.practice.domain.attempt.ExamFormat;
import com.toefly.practice.domain.attempt.Section;
import com.toefly.practice.domain.attempt.TaskType;
import com.toefly.practice.domain.scoring.ItemSetKeys;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An item set version as practice-service needs it: what the learner may see before answering, what the review
 * adds, and the keys. The keys never leave practice_db (ADR-0010).
 *
 * @param stimulus stimulus without keys and transcripts
 * @param reviewStimulus stimulus with transcripts, shown after scoring (AC-11.01.6)
 * @param items question content without keys by item ordinal
 */
public record ItemSetContent(
        UUID itemSetId,
        int version,
        TaskType taskType,
        @Nullable Section section,
        ExamFormat examFormat,
        String status,
        int estimatedSeconds,
        @Nullable Map<String, Object> stimulus,
        @Nullable Map<String, Object> reviewStimulus,
        Map<Integer, Map<String, Object>> items,
        Map<Integer, Double> maxScores,
        ItemSetKeys keys) {

    public ItemSetContent {
        items = Map.copyOf(items);
        maxScores = Map.copyOf(maxScores);
    }
}
