package com.toefly.planning.application.port.in;

import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.WeekSection;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Local copies of what other services know: θ, the catalog of item sets, erasure. */
public interface SyncLearnerUseCase {

    void abilityUpdated(UUID userId, WeekSection section, double theta, Instant occurredAt);

    void itemSetPublished(
            UUID itemSetId,
            int version,
            TaskType taskType,
            String examFormat,
            double difficultyB,
            double estimatedMinutes,
            String topic,
            @Nullable UUID ownerTutorId,
            Instant occurredAt);

    void itemSetHidden(UUID itemSetId, Instant occurredAt);

    /** {@code account-deletion-requested}: the plan, the weeks and the signals of the user (docs/03 §14). */
    void deleteUser(UUID userId);
}
