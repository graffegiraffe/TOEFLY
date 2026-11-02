package ${package}.application.port.in;

import ${package}.domain.item.Item;

public interface CreateItemUseCase {

    Item create(CreateItemCommand command);
}
