package ${package}.adapter.out.messaging;

import ${package}.application.port.out.ItemEventPublisherPort;
import ${package}.domain.item.Item;
import com.toefly.common.outbox.IntegrationEvent;
import com.toefly.common.outbox.IntegrationEventPublisher;
import com.toefly.events.${serviceShort}.ItemCreatedV1;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OutboxItemEventPublisherAdapter implements ItemEventPublisherPort {

    static final String ITEM_CREATED = "toefly.${topicSegment}.item-created.v1";

    private final IntegrationEventPublisher publisher;

    public OutboxItemEventPublisherAdapter(IntegrationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void itemCreated(Item item, UUID eventId) {
        ItemCreatedV1 payload = ItemCreatedV1.newBuilder()
                .setEventId(eventId.toString())
                .setOccurredAt(item.createdAt())
                .setUserId(item.ownerId().toString())
                .setCorrelationId(null)
                .setCausationId(null)
                .setItemId(item.id().toString())
                .setTitleLength(item.title().length())
                .build();
        publisher.publish(new IntegrationEvent(
                eventId,
                ITEM_CREATED,
                "Item",
                item.id().toString(),
                item.ownerId().toString(),
                "items/" + item.id(),
                item.createdAt(),
                item.ownerId(),
                null,
                null,
                payload));
    }
}
