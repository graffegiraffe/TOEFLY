package com.toefly.common.test.contract;

import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroSerdeConfig;
import io.apicurio.registry.serde.config.SerdeConfig;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.avro.generic.GenericData;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads what Debezium would send to Kafka (ADR-0031, docs/04-architecture.md §6.5): the new {@code outbox_event} row
 * of a topic, the headers of the EventRouter placement, the partition key and the Avro payload decoded through
 * Apicurio.
 */
public final class OutboxMessageReceiver implements MessageReceiver, AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;
    private final AvroKafkaDeserializer<Object> deserializer = new AvroKafkaDeserializer<>();

    public OutboxMessageReceiver(JdbcClient jdbc, String registryUrl) {
        this.jdbc = jdbc;
        deserializer.configure(
                Map.of(SerdeConfig.REGISTRY_URL, registryUrl, AvroSerdeConfig.USE_SPECIFIC_AVRO_READER, false), false);
    }

    @Override
    public Object mark(String topic) {
        return Set.copyOf(jdbc.sql("SELECT id FROM outbox_event WHERE topic = :topic")
                .param("topic", topic)
                .query(UUID.class)
                .list());
    }

    @Override
    @SuppressWarnings("unchecked")
    public ContractMessage receive(String topic, Object mark) {
        Set<UUID> before = (Set<UUID>) mark;
        List<Row> rows = jdbc
                .sql("""
                        SELECT id, event_type, source, subject, occurred_at, data_schema, payload, trace_parent,
                               correlation_id, causation_id, partition_key, spec_version
                        FROM outbox_event WHERE topic = :topic ORDER BY created_at, id
                        """)
                .param("topic", topic)
                .query((rs, n) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("source"),
                        rs.getString("subject"),
                        rs.getTimestamp("occurred_at"),
                        rs.getString("data_schema"),
                        rs.getBytes("payload"),
                        rs.getString("trace_parent"),
                        rs.getString("correlation_id"),
                        rs.getString("causation_id"),
                        rs.getString("partition_key"),
                        rs.getString("spec_version")))
                .list()
                .stream()
                .filter(row -> !before.contains(row.id()))
                .toList();
        if (rows.isEmpty()) {
            throw new AssertionError("The trigger published nothing to " + topic);
        }
        Row row = rows.getLast();
        Map<String, String> headers = new LinkedHashMap<>();
        // Same placement as transforms.outbox.table.fields.additional.placement of the outbox connector.
        headers.put("ce_id", row.id().toString());
        headers.put("ce_type", row.eventType());
        headers.put("ce_source", row.source());
        headers.put("ce_subject", String.valueOf(row.subject()));
        headers.put(
                "ce_time", DateTimeFormatter.ISO_INSTANT.format(row.occurredAt().toInstant()));
        headers.put("ce_dataschema", row.dataSchema());
        headers.put("traceparent", String.valueOf(row.traceParent()));
        headers.put("ce_correlationid", String.valueOf(row.correlationId()));
        headers.put("ce_causationid", String.valueOf(row.causationId()));
        headers.put("ce_partitionkey", row.partitionKey());
        headers.put("ce_specversion", row.specVersion());
        headers.put("content-type", "application/avro");
        Object record = deserializer.deserialize(topic, row.payload());
        Object body = JSON.readValue(GenericData.get().toString(record), Object.class);
        return new ContractMessage(topic, row.partitionKey(), headers, body);
    }

    @Override
    public void close() {
        deserializer.close();
    }

    private record Row(
            UUID id,
            String eventType,
            String source,
            String subject,
            Timestamp occurredAt,
            String dataSchema,
            byte[] payload,
            String traceParent,
            String correlationId,
            String causationId,
            String partitionKey,
            String specVersion) {}
}
