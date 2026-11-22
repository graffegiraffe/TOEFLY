package com.toefly.billing.application.port.out;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Correlation and causation of an outgoing event (CloudEvents ce_correlationid, ce_causationid). */
public record Cause(@Nullable UUID correlationId, @Nullable UUID causationId) {

    public static Cause none() {
        return new Cause(null, null);
    }

    public static Cause of(UUID correlationId) {
        return new Cause(correlationId, null);
    }
}
