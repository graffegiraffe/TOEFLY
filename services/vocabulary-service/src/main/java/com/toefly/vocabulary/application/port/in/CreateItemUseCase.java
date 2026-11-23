package com.toefly.vocabulary.application.port.in;

import com.toefly.vocabulary.domain.item.Item;

public interface CreateItemUseCase {

    Item create(CreateItemCommand command);
}
