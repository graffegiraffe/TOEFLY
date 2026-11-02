package com.toefly.common.outbox;

import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Inserts outbox rows with plain JDBC so the row joins the caller's transaction. */
public class JdbcOutboxWriter implements OutboxWriter {

    private final JdbcClient jdbc;

    public JdbcOutboxWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(OutboxRow row) {
        jdbc.sql("""
                        INSERT INTO outbox_event (id, aggregate_type, aggregate_id, topic, partition_key, event_type,
                            source, subject, occurred_at, data_schema, payload, trace_parent, correlation_id,
                            causation_id, user_id)
                        VALUES (:id, :aggregateType, :aggregateId, :topic, :partitionKey, :eventType, :source,
                            :subject, :occurredAt, :dataSchema, :payload, :traceParent, :correlationId,
                            :causationId, :userId)
                        """)
                .param("id", row.id())
                .param("aggregateType", row.aggregateType())
                .param("aggregateId", row.aggregateId())
                .param("topic", row.topic())
                .param("partitionKey", row.partitionKey())
                .param("eventType", row.eventType())
                .param("source", row.source())
                .param("subject", row.subject())
                .param("occurredAt", Timestamp.from(row.occurredAt()))
                .param("dataSchema", row.dataSchema())
                .param("payload", row.payload())
                .param("traceParent", row.traceParent())
                .param("correlationId", row.correlationId())
                .param("causationId", row.causationId())
                .param("userId", row.userId())
                .update();
    }
}
