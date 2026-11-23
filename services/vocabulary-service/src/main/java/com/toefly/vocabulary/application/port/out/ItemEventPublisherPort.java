package com.toefly.vocabulary.application.port.out;

import com.toefly.vocabulary.domain.item.Item;
import java.util.UUID;

public interface ItemEventPublisherPort {

    void itemCreated(Item item, UUID eventId);
}
