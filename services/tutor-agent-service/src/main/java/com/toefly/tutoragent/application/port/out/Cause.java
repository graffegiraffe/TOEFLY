package com.toefly.tutoragent.application.port.out;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Correlation and causation of an event this service publishes because of another one. */
public record Cause(@Nullable UUID correlationId, @Nullable UUID causationId) {

    public static Cause none() {
        return new Cause(null, null);
    }
}
