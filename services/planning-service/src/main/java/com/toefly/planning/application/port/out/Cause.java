package com.toefly.planning.application.port.out;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The chain an event belongs to: the incoming event or request. */
public record Cause(@Nullable UUID correlationId, @Nullable UUID causationId) {

    public static Cause none() {
        return new Cause(null, null);
    }
}
