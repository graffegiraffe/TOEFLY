package com.toefly.archgood.application.port.in;

import com.toefly.archgood.domain.Item;

public interface CreateItemUseCase {

    Item create(String title);
}
