package com.toefly.content.adapter.out.messaging;

import com.toefly.common.outbox.IntegrationEvent;
import com.toefly.common.outbox.IntegrationEventPublisher;
import com.toefly.content.application.port.out.ContentEventPublisherPort;
import com.toefly.content.application.port.out.ItemSetStorePort.ItemRow;
import com.toefly.content.domain.catalog.ItemSet;
import com.toefly.events.content.ItemSetHiddenV1;
import com.toefly.events.content.ItemSetPublishedV1;
import com.toefly.events.content.PublishedItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Content events in the canonical topics (docs/00-README.md §6), keyed by {@code itemSetId}. */
@Component
public class OutboxContentEventPublisherAdapter implements ContentEventPublisherPort {

    static final String ITEM_SET_PUBLISHED = "toefly.content.item-set-published.v1";
    static final String ITEM_SET_HIDDEN = "toefly.content.item-set-hidden.v1";

    private final IntegrationEventPublisher publisher;

    public OutboxContentEventPublisherAdapter(IntegrationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void itemSetPublished(ItemSet s, List<ItemRow> items, Instant now) {
        UUID eventId = UUID.randomUUID();
        ItemSetPublishedV1 payload = ItemSetPublishedV1.newBuilder()
                .setEventId(eventId)
                .setOccurredAt(now)
                .setItemSetId(s.id())
                .setVersion(s.version())
                .setTaskType(s.taskType().name())
                .setSection(s.section() == null ? null : s.section().name())
                .setExamFormat(s.examFormat().name())
                .setCefr(s.cefr())
                .setDifficultyB(s.bMean())
                .setItemCount(s.itemCount())
                .setEstimatedMinutes(s.estimatedMinutes())
                .setTopic(s.topic())
                .setSkills(s.skills())
                .setOwnerTutorId(s.ownerTutorId())
                .setItems(items.stream()
                        .map(i -> PublishedItem.newBuilder()
                                .setItemId(i.id())
                                .setOrdinal(i.ordinal())
                                .setB(i.b())
                                .build())
                        .toList())
                .build();
        publish(eventId, ITEM_SET_PUBLISHED, s, now, payload);
    }

    @Override
    public void itemSetHidden(ItemSet s, Instant now) {
        UUID eventId = UUID.randomUUID();
        ItemSetHiddenV1 payload = ItemSetHiddenV1.newBuilder()
                .setEventId(eventId)
                .setOccurredAt(now)
                .setItemSetId(s.id())
                .setReason(s.hiddenReason() == null ? "ADMIN" : s.hiddenReason().name())
                .build();
        publish(eventId, ITEM_SET_HIDDEN, s, now, payload);
    }

    private void publish(UUID eventId, String topic, ItemSet s, Instant now,
            org.apache.avro.specific.SpecificRecord payload) {
        publisher.publish(new IntegrationEvent(eventId, topic, "ItemSet", s.id().toString(), s.id().toString(),
                "item-sets/" + s.id(), now, null, null, null, payload));
    }
}
