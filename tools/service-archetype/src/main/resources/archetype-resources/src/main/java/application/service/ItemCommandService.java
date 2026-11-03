package ${package}.application.service;

import ${package}.application.port.in.CreateItemCommand;
import ${package}.application.port.in.CreateItemUseCase;
import ${package}.application.port.in.MarkItemAnnouncedUseCase;
import ${package}.application.port.out.ItemEventPublisherPort;
import ${package}.application.port.out.ItemStorePort;
import ${package}.domain.item.Item;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemCommandService implements CreateItemUseCase, MarkItemAnnouncedUseCase {

    private final ItemStorePort items;
    private final ItemEventPublisherPort events;
    private final Clock clock;

    public ItemCommandService(ItemStorePort items, ItemEventPublisherPort events, Clock clock) {
        this.items = items;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Item create(CreateItemCommand command) {
        Item item = Item.create(UUID.randomUUID(), command.ownerId(), command.title(), clock.instant());
        items.save(item);
        events.itemCreated(item, UUID.randomUUID());
        return item;
    }

    @Override
    @Transactional
    public void markAnnounced(UUID itemId) {
        items.findById(itemId).map(Item::markAnnounced).ifPresent(items::save);
    }
}
