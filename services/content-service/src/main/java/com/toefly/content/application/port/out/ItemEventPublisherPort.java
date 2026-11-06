package com.toefly.content.application.port.out;

import com.toefly.content.domain.item.Item;
import java.util.UUID;

public interface ItemEventPublisherPort {

    void itemCreated(Item item, UUID eventId);
}
