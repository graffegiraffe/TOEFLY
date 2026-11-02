package com.toefly.archgood.adapter.in.kafka;

import com.toefly.common.outbox.ProcessedEventGuard;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;

public class ItemCreatedListener {

    private final ProcessedEventGuard guard;

    public ItemCreatedListener(ProcessedEventGuard guard) {
        this.guard = guard;
    }

    @KafkaListener(topics = "toefly.demo.item-created.v1")
    void on(ConsumerRecord<String, byte[]> record) {
        guard.runOnce(record, "ItemCreatedListener", () -> {});
    }
}
