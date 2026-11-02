package com.toefly.archgood.config;

import com.toefly.archgood.adapter.out.persistence.JdbcItemStoreAdapter;
import com.toefly.archgood.application.service.ItemCommandService;

public class ItemConfig {

    ItemCommandService itemCommandService() {
        return new ItemCommandService(new JdbcItemStoreAdapter());
    }
}
