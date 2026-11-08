package com.toefly.content.application.seed;

import com.toefly.content.domain.catalog.Provenance;
import com.toefly.content.domain.catalog.TaskType;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A verified {@code ITEM_SET} envelope, ready to be stored (docs/08-content-pipeline.md §4.7). */
public record SeedItemSet(
        String seedId,
        UUID id,
        String sourceCode,
        TaskType taskType,
        Provenance provenance,
        @Nullable String cefr,
        String topic,
        @Nullable String domain,
        List<String> skills,
        @Nullable Integer sourcePage,
        @Nullable String sourceItemNo,
        boolean reviewed,
        boolean needsReview,
        double audioSeconds,
        String payloadJson,
        String contentHash,
        String searchText,
        List<SeedItem> items,
        Extraction extraction) {

    public record SeedItem(UUID id, int ordinal, @Nullable String subskill, int maxScore) {}
}
