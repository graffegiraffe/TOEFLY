package com.toefly.content.application.port.in;

import com.toefly.content.domain.item.Item;

public interface CreateItemUseCase {

    Item create(CreateItemCommand command);
}
