package com.toefly.archgood.adapter.in.web;

import com.toefly.archgood.application.port.in.CreateItemUseCase;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ItemController {

    private final CreateItemUseCase createItem;

    public ItemController(CreateItemUseCase createItem) {
        this.createItem = createItem;
    }

    @PostMapping("/items")
    String create() {
        return createItem.create("title").id();
    }
}
