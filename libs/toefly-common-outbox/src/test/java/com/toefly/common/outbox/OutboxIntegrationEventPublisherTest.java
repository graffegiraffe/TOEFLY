package com.toefly.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.common.events.EventPayloadSerializer;
import com.toefly.common.events.EventPayloadSerializer.SerializedPayload;
import com.toefly.common.observability.TraceparentProvider;
import io.micrometer.tracing.test.simple.SimpleTracer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class OutboxIntegrationEventPublisherTest {

    private static final UUID USER_ID = UUID.fromString("7b1c9a44-0f3e-4f0b-9a60-6a53f1d2c871");
    private static final String TOPIC = "toefly.demo.item-created.v1";

    private final List<OutboxRow> rows = new ArrayList<>();
    private final EventPayloadSerializer serializer =
            (topic, record) -> new SerializedPayload(new byte[] {1, 2, 3}, "apicurio://toefly/" + topic + "-value/1");
    private final SimpleTracer tracer = new SimpleTracer();
    private final OutboxIntegrationEventPublisher publisher =
            new OutboxIntegrationEventPublisher(rows::add, serializer, new TraceparentProvider(tracer), "demo-service");

    @AfterEach
    void endTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void writesCloudEventsAttributesAndTraceIntoOutboxRow() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        IntegrationEvent event = event();
        var span = tracer.nextSpan().start();

        try (var scope = tracer.withSpan(span)) {
            publisher.publish(event);
        } finally {
            span.end();
        }

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(event.eventId());
            assertThat(row.topic()).isEqualTo(TOPIC);
            assertThat(row.eventType()).isEqualTo(TOPIC);
            assertThat(row.source()).isEqualTo("/toefly/demo-service");
            assertThat(row.subject()).isEqualTo("items/42");
            assertThat(row.partitionKey()).isEqualTo(USER_ID.toString());
            assertThat(row.dataSchema()).isEqualTo("apicurio://toefly/" + TOPIC + "-value/1");
            assertThat(row.payload()).containsExactly(1, 2, 3);
            assertThat(row.traceParent())
                    .startsWith("00-")
                    .contains(span.context().spanId());
            assertThat(row.userId()).isEqualTo(USER_ID);
        });
    }

    @Test
    void refusesToPublishOutsideTransaction() {
        assertThatThrownBy(() -> publisher.publish(event())).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(rows).isEmpty();
    }

    private static IntegrationEvent event() {
        return new IntegrationEvent(
                UUID.randomUUID(),
                TOPIC,
                "Item",
                "42",
                USER_ID.toString(),
                "items/42",
                Instant.parse("2026-11-01T09:30:00Z"),
                USER_ID,
                null,
                null,
                new FakeRecord());
    }

    static final class FakeRecord extends GenericData.Record implements SpecificRecord {

        private static final Schema SCHEMA =
                SchemaBuilder.record("Fake").fields().endRecord();

        FakeRecord() {
            super(SCHEMA);
        }
    }
}
