package com.toefly.vocabulary.adapter.in.kafka;

import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.vocabulary.ItemCreatedV1;
import com.toefly.vocabulary.application.port.in.MarkItemAnnouncedUseCase;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

@Component
public class ItemCreatedListener {

    private final ProcessedEventGuard guard;
    private final MarkItemAnnouncedUseCase markAnnounced;

    public ItemCreatedListener(ProcessedEventGuard guard, MarkItemAnnouncedUseCase markAnnounced) {
        this.guard = guard;
        this.markAnnounced = markAnnounced;
    }

    @ToeflyListener(id = "item-created", topics = "toefly.vocabulary.item-created.v1")
    void on(ConsumerRecord<String, ItemCreatedV1> record) {
        guard.runOnce(
                record,
                "ItemCreatedListener",
                () -> markAnnounced.markAnnounced(UUID.fromString(record.value().getItemId())));
    }
}
