package com.toefly.analytics.application.port.out;

import com.toefly.analytics.application.port.in.RebuildJob;
import java.util.Optional;
import java.util.UUID;

/** {@code projection_rebuild_job}: at most one running rebuild per read model. */
public interface RebuildJobRepository {

    /** @return {@code false} when a rebuild of the same read model is running */
    boolean start(RebuildJob job, UUID idempotencyKey);

    void update(RebuildJob job);

    Optional<RebuildJob> find(UUID id);

    Optional<RebuildJob> findByKey(UUID adminId, UUID idempotencyKey);

    /** Marks rebuilds left running by a stopped instance as failed. */
    void failRunning(String reason);
}
