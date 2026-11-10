package com.toefly.practice.adapter.in.kafka;

import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.content.ItemSetHiddenV1;
import com.toefly.events.content.ItemSetPublishedV1;
import com.toefly.practice.application.port.in.SyncItemPoolUseCase;
import com.toefly.practice.application.port.out.ItemPoolPort.PoolItem;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/** Published and hidden sets: identifiers and difficulties of items, and which sets accept new attempts. */
@Component
public class ContentEventsListener {

    private final ProcessedEventGuard guard;
    private final SyncItemPoolUseCase pool;

    public ContentEventsListener(ProcessedEventGuard guard, SyncItemPoolUseCase pool) {
        this.guard = guard;
        this.pool = pool;
    }

    @ToeflyListener(id = "item-set-published", topics = "toefly.content.item-set-published.v1")
    void onPublished(ConsumerRecord<String, ItemSetPublishedV1> record) {
        ItemSetPublishedV1 event = record.value();
        guard.runOnce(
                record,
                "ContentEventsListener.published",
                () -> pool.published(
                        event.getItemSetId(),
                        event.getVersion(),
                        event.getExamFormat(),
                        event.getItems().stream()
                                .map(item -> new PoolItem(item.getItemId(), item.getOrdinal(), item.getB()))
                                .toList()));
    }

    @ToeflyListener(id = "item-set-hidden", topics = "toefly.content.item-set-hidden.v1")
    void onHidden(ConsumerRecord<String, ItemSetHiddenV1> record) {
        guard.runOnce(
                record,
                "ContentEventsListener.hidden",
                () -> pool.hidden(record.value().getItemSetId()));
    }
}
