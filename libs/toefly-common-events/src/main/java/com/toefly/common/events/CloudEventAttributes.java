package com.toefly.common.events;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.jspecify.annotations.Nullable;

/** CloudEvents attributes of one TOEFLY event, read from or written to Kafka headers. */
public record CloudEventAttributes(
        UUID id,
        String source,
        String type,
        @Nullable String subject,
        Instant time,
        String dataSchema,
        String partitionKey,
        @Nullable UUID correlationId,
        @Nullable UUID causationId,
        @Nullable String traceparent) {

    public CloudEventAttributes {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        TopicNames.requireValid(Objects.requireNonNull(type, "type"));
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(dataSchema, "dataSchema");
        Objects.requireNonNull(partitionKey, "partitionKey");
    }

    public static CloudEventAttributes fromHeaders(Headers headers) {
        return new CloudEventAttributes(
                UUID.fromString(required(headers, CloudEventHeaders.ID)),
                required(headers, CloudEventHeaders.SOURCE),
                required(headers, CloudEventHeaders.TYPE),
                optional(headers, CloudEventHeaders.SUBJECT),
                Instant.parse(required(headers, CloudEventHeaders.TIME)),
                required(headers, CloudEventHeaders.DATA_SCHEMA),
                required(headers, CloudEventHeaders.PARTITION_KEY),
                optionalUuid(headers, CloudEventHeaders.CORRELATION_ID),
                optionalUuid(headers, CloudEventHeaders.CAUSATION_ID),
                optional(headers, CloudEventHeaders.TRACEPARENT));
    }

    public void writeTo(Headers headers) {
        put(headers, CloudEventHeaders.SPEC_VERSION, CloudEventHeaders.SPEC_VERSION_VALUE);
        put(headers, CloudEventHeaders.ID, id.toString());
        put(headers, CloudEventHeaders.SOURCE, source);
        put(headers, CloudEventHeaders.TYPE, type);
        put(headers, CloudEventHeaders.SUBJECT, subject);
        put(headers, CloudEventHeaders.TIME, time.toString());
        put(headers, CloudEventHeaders.DATA_SCHEMA, dataSchema);
        put(headers, CloudEventHeaders.PARTITION_KEY, partitionKey);
        put(headers, CloudEventHeaders.CORRELATION_ID, correlationId == null ? null : correlationId.toString());
        put(headers, CloudEventHeaders.CAUSATION_ID, causationId == null ? null : causationId.toString());
        put(headers, CloudEventHeaders.CONTENT_TYPE, CloudEventHeaders.AVRO_CONTENT_TYPE);
        put(headers, CloudEventHeaders.TRACEPARENT, traceparent);
    }

    private static void put(Headers headers, String name, @Nullable String value) {
        headers.remove(name);
        if (value != null) {
            headers.add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String required(Headers headers, String name) {
        String value = optional(headers, name);
        if (value == null) {
            throw new MissingCloudEventHeaderException(name);
        }
        return value;
    }

    private static @Nullable String optional(Headers headers, String name) {
        Header header = headers.lastHeader(name);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static @Nullable UUID optionalUuid(Headers headers, String name) {
        String value = optional(headers, name);
        return value == null ? null : UUID.fromString(value);
    }
}
