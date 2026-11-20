package com.toefly.analytics.application.port.out;

import com.toefly.analytics.domain.item.Item;
import java.util.UUID;

public interface ItemEventPublisherPort {

    void itemCreated(Item item, UUID eventId);
}
