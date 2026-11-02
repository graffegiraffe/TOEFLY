package com.toefly.common.outbox;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One row of {@code outbox_event}. */
public record OutboxRow(
        UUID id,
        String aggregateType,
        String aggregateId,
        String topic,
        String partitionKey,
        String eventType,
        String source,
        @Nullable String subject,
        Instant occurredAt,
        String dataSchema,
        byte[] payload,
        @Nullable String traceParent,
        @Nullable UUID correlationId,
        @Nullable UUID causationId,
        @Nullable UUID userId) {}
