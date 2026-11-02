package ${package}.application.port.out;

import ${package}.domain.item.Item;
import java.util.UUID;

public interface ItemEventPublisherPort {

    void itemCreated(Item item, UUID eventId);
}
