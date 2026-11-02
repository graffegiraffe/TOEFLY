package com.toefly.archgood.adapter.out.persistence;

import com.toefly.archgood.application.port.out.ItemStorePort;
import com.toefly.archgood.domain.Item;

public class JdbcItemStoreAdapter implements ItemStorePort {

    @Override
    public void save(Item item) {}
}
