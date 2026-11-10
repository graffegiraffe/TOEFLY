package com.toefly.planning.application.port.out;

import com.toefly.planning.domain.week.TaskType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Local catalog of approved item sets, built from the events of content-service (docs/12, phase 4, decision 10). */
public interface ItemSetCatalogPort {

    void published(CatalogEntry entry, Instant occurredAt);

    void hidden(UUID itemSetId, Instant occurredAt);

    /** Visible sets of a type in the format of the exam, without sets of tutors. */
    List<CatalogEntry> visible(TaskType type);

    record CatalogEntry(
            UUID itemSetId,
            int version,
            TaskType taskType,
            String examFormat,
            double difficultyB,
            double estimatedMinutes,
            String topic,
            @Nullable UUID ownerTutorId) {}
}
