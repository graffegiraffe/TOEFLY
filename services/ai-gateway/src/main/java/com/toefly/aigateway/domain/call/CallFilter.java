package com.toefly.aigateway.domain.call;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Filters of the call journal in the admin. */
public record CallFilter(
        @Nullable String promptKey,
        @Nullable String provider,
        @Nullable String outcome,
        @Nullable Instant from,
        @Nullable Instant to) {}
