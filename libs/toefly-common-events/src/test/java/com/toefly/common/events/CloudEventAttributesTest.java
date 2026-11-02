package com.toefly.common.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

class CloudEventAttributesTest {

    private final CloudEventAttributes attributes = new CloudEventAttributes(
            UUID.fromString("0193f2a4-9d10-7a3c-8e41-5b7e2c9d0f12"),
            "/toefly/practice-service",
            "toefly.practice.attempt-scored.v1",
            "attempts/0193f2a4-6c1e-7b9a-9a51-3c0c3e1f7d20",
            Instant.parse("2026-11-01T09:30:12.480Z"),
            "apicurio://toefly/toefly.practice.attempt-scored.v1-value/1",
            "7b1c9a44-0f3e-4f0b-9a60-6a53f1d2c871",
            UUID.fromString("0193f2a4-6c00-7123-8f20-1b2c3d4e5f60"),
            null,
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

    @Test
    void roundTripsThroughKafkaHeaders() {
        RecordHeaders headers = new RecordHeaders();

        attributes.writeTo(headers);

        assertThat(CloudEventAttributes.fromHeaders(headers)).isEqualTo(attributes);
        assertThat(value(headers, CloudEventHeaders.SPEC_VERSION)).isEqualTo("1.0");
        assertThat(value(headers, CloudEventHeaders.CONTENT_TYPE)).isEqualTo("application/avro");
        assertThat(headers.lastHeader(CloudEventHeaders.CAUSATION_ID)).isNull();
    }

    @Test
    void missingMandatoryHeaderIsNotRetryable() {
        RecordHeaders headers = new RecordHeaders();
        attributes.writeTo(headers);
        headers.remove(CloudEventHeaders.ID);

        assertThatThrownBy(() -> CloudEventAttributes.fromHeaders(headers))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("ce_id");
    }

    private static String value(RecordHeaders headers, String name) {
        return new String(headers.lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
