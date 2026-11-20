package com.toefly.analytics.application.port.in;

import com.toefly.analytics.domain.item.Item;

public interface CreateItemUseCase {

    Item create(CreateItemCommand command);
}
