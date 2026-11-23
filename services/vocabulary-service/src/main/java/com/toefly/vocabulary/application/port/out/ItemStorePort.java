package com.toefly.vocabulary.application.port.out;

import com.toefly.vocabulary.domain.item.Item;
import java.util.Optional;
import java.util.UUID;

public interface ItemStorePort {

    void save(Item item);

    Optional<Item> findById(UUID id);
}
