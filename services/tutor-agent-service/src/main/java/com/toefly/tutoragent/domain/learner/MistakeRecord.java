package com.toefly.tutoragent.domain.learner;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One mistake of the learner (docs/06 §6.1): append-only; the excerpt and correction are the learner's own text and
 * stay inside tutor_agent_db.
 */
public record MistakeRecord(
        UUID id,
        UUID userId,
        String subcategory,
        SourceType sourceType,
        UUID sourceId,
        @Nullable UUID itemId,
        @Nullable String taskType,
        @Nullable String excerpt,
        @Nullable String correction,
        @Nullable String explanation,
        Severity severity,
        List<String> unitRefs,
        Instant createdAt) {

    public MistakeRecord {
        MistakeCategory.ofSubcategory(subcategory);
        unitRefs = List.copyOf(unitRefs);
        excerpt = clip(excerpt, 300);
        correction = clip(correction, 300);
        explanation = clip(explanation, 400);
    }

    public MistakeCategory category() {
        return MistakeCategory.ofSubcategory(subcategory);
    }

    /** Text of the vector memory: {@code Wrong: … | Right: … | Rule: …}, at most 400 characters (docs/06 §6.4). */
    public String memoryText() {
        StringBuilder text = new StringBuilder("Wrong: ").append(excerpt == null ? "" : excerpt);
        if (correction != null) {
            text.append(" | Right: ").append(correction);
        }
        if (explanation != null) {
            text.append(" | Rule: ").append(explanation);
        }
        return text.length() <= 400 ? text.toString() : text.substring(0, 400);
    }

    private static @Nullable String clip(@Nullable String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
