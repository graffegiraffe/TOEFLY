package ${package}.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import ${package}.application.port.in.CreateItemCommand;
import ${package}.application.port.out.ItemEventPublisherPort;
import ${package}.application.port.out.ItemStorePort;
import ${package}.domain.item.Item;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItemCommandServiceTest {

    private final Map<UUID, Item> store = new HashMap<>();
    private final List<Item> published = new ArrayList<>();
    private final ItemStorePort items = new ItemStorePort() {
        @Override
        public void save(Item item) {
            store.put(item.id(), item);
        }

        @Override
        public Optional<Item> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }
    };
    private final ItemEventPublisherPort events = (item, eventId) -> published.add(item);
    private final ItemCommandService service = new ItemCommandService(
            items, events, Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void createStoresItemAndPublishesEvent() {
        Item item = service.create(new CreateItemCommand(UUID.randomUUID(), "Week 7"));

        assertThat(store).containsKey(item.id());
        assertThat(published).containsExactly(item);
    }

    @Test
    void markAnnouncedUpdatesStoredItem() {
        Item item = service.create(new CreateItemCommand(UUID.randomUUID(), "Week 7"));

        service.markAnnounced(item.id());

        assertThat(store.get(item.id()).announced()).isTrue();
    }
}
