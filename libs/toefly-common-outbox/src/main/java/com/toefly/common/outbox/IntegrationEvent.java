package com.toefly.common.outbox;

import com.toefly.common.events.TopicNames;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.jspecify.annotations.Nullable;

/**
 * An event to publish. {@code eventId}, {@code occurredAt}, {@code userId}, {@code correlationId} and
 * {@code causationId} must equal the envelope fields of the Avro {@code payload}.
 *
 * @param partitionKey {@code userId}, or the aggregate id for events without a user
 * @param subject aggregate path, for example {@code attempts/<attemptId>}
 */
public record IntegrationEvent(
        UUID eventId,
        String topic,
        String aggregateType,
        String aggregateId,
        String partitionKey,
        @Nullable String subject,
        Instant occurredAt,
        @Nullable UUID userId,
        @Nullable UUID correlationId,
        @Nullable UUID causationId,
        SpecificRecord payload) {

    public IntegrationEvent {
        Objects.requireNonNull(eventId, "eventId");
        TopicNames.requireValid(topic);
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(partitionKey, "partitionKey");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(payload, "payload");
    }
}
