package com.toefly.analytics.application.port.in.event;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Envelope of a consumed event: CloudEvents id and type (the topic), the moment of the fact and the learner. */
public record EventMeta(
        UUID eventId,
        String type,
        Instant occurredAt,
        @Nullable UUID userId) {}
