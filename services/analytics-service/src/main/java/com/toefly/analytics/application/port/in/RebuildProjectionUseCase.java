package com.toefly.analytics.application.port.in;

import java.util.UUID;

/**
 * Rebuild of a read model (ADR-0011, docs/04 §6.7): the journal is replayed into empty tables in one transaction, so
 * readers keep the old view until the new one commits; events arriving meanwhile wait and apply on top.
 */
public interface RebuildProjectionUseCase {

    /**
     * @return the job and whether it was started by an earlier request with the same key
     */
    RebuildStart start(ProjectionName projection, UUID adminId, UUID idempotencyKey);

    RebuildJob get(UUID jobId);

    /** Marks rebuilds a stopped instance left running as failed, so a new one can start. */
    void recoverInterrupted();
}
