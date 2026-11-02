package com.toefly.archgood.application.port.out;

import com.toefly.archgood.domain.Item;

public interface ItemStorePort {

    void save(Item item);
}
