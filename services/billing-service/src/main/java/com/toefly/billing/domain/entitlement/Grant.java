package com.toefly.billing.domain.entitlement;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A right with its source, limit and term ({@code entitlement_grant}). */
public record Grant(
        Entitlement entitlement,
        EntitlementSource source,
        @Nullable UUID sourceId,
        @Nullable Integer limit,
        @Nullable Integer used,
        @Nullable Instant validUntil) {

    public Grant {
        Objects.requireNonNull(entitlement, "entitlement");
        Objects.requireNonNull(source, "source");
    }

    public boolean limited() {
        return limit != null;
    }
}
