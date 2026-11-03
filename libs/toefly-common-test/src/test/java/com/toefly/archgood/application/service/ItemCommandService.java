package com.toefly.archgood.application.service;

import com.toefly.archgood.application.port.in.CreateItemUseCase;
import com.toefly.archgood.application.port.out.ItemStorePort;
import com.toefly.archgood.domain.Item;
import org.springframework.transaction.annotation.Transactional;

public class ItemCommandService implements CreateItemUseCase {

    private final ItemStorePort store;

    public ItemCommandService(ItemStorePort store) {
        this.store = store;
    }

    @Override
    @Transactional
    public Item create(String title) {
        Item item = new Item("1", title);
        store.save(item);
        return item;
    }
}
