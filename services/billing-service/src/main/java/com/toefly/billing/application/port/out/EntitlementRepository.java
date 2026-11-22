package com.toefly.billing.application.port.out;

import com.toefly.billing.domain.entitlement.EntitlementSet;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** {@code entitlement_grant}, {@code entitlement_snapshot} and the Free AI check counter. */
public interface EntitlementRepository {

    Optional<Snapshot> lockSnapshot(UUID userId);

    Optional<Snapshot> findSnapshot(UUID userId);

    /** Replaces the grants of the user and stores the snapshot with its version. */
    void store(EntitlementSet set, long version, Instant changedAt);

    int freeUsed(UUID userId);

    void countFreeCheck(UUID userId, Instant now);

    void deleteUser(UUID userId);

    record Snapshot(long version, String fingerprint, Instant changedAt) {}
}
