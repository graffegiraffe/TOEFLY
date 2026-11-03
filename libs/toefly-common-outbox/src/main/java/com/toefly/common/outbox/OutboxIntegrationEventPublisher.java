package com.toefly.common.outbox;

import com.toefly.common.events.EventPayloadSerializer;
import com.toefly.common.events.EventPayloadSerializer.SerializedPayload;
import com.toefly.common.observability.TraceparentProvider;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Serializes the Avro body and writes it to {@code outbox_event} in the current transaction (propagation
 * {@code MANDATORY}); Debezium routes the row to the topic with CloudEvents headers.
 */
public class OutboxIntegrationEventPublisher implements IntegrationEventPublisher {

    private final OutboxWriter writer;
    private final EventPayloadSerializer serializer;
    private final @Nullable TraceparentProvider traceparent;
    private final String source;

    public OutboxIntegrationEventPublisher(
            OutboxWriter writer,
            EventPayloadSerializer serializer,
            @Nullable TraceparentProvider traceparent,
            String serviceName) {
        this.writer = writer;
        this.serializer = serializer;
        this.traceparent = traceparent;
        this.source = "/toefly/" + serviceName;
    }

    @Override
    public void publish(IntegrationEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException(
                    "Events are published only inside the transaction of the business change");
        }
        SerializedPayload payload = serializer.serialize(event.topic(), event.payload());
        writer.insert(new OutboxRow(
                event.eventId(),
                event.aggregateType(),
                event.aggregateId(),
                event.topic(),
                event.partitionKey(),
                event.topic(),
                source,
                event.subject(),
                event.occurredAt(),
                payload.dataSchema(),
                payload.bytes(),
                traceparent == null ? null : traceparent.current().orElse(null),
                event.correlationId(),
                event.causationId(),
                event.userId()));
    }
}
