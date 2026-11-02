package com.toefly.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.common.events.CloudEventHeaders;
import com.toefly.common.events.NonRetryableEventException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class ProcessedEventGuardTest {

    @Test
    void readsEventIdFromCeIdHeader() {
        UUID id = UUID.randomUUID();
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>("toefly.demo.item-created.v1", 0, 5, "k", null);
        record.headers().add(CloudEventHeaders.ID, id.toString().getBytes(StandardCharsets.UTF_8));

        assertThat(ProcessedEventGuard.eventId(record)).isEqualTo(id);
    }

    @Test
    void recordWithoutCeIdIsNotRetryable() {
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>("toefly.demo.item-created.v1", 0, 5, "k", null);

        assertThatThrownBy(() -> ProcessedEventGuard.eventId(record)).isInstanceOf(NonRetryableEventException.class);
    }
}
