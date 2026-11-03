package ${package}.adapter.in.web;

import ${package}.adapter.in.web.api.ItemsApi;
import ${package}.adapter.in.web.dto.CreateItemRequest;
import ${package}.adapter.in.web.dto.ItemResponse;
import ${package}.application.port.in.CreateItemCommand;
import ${package}.application.port.in.CreateItemUseCase;
import ${package}.domain.item.Item;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ItemController implements ItemsApi {

    private final CreateItemUseCase createItem;

    public ItemController(CreateItemUseCase createItem) {
        this.createItem = createItem;
    }

    @Override
    public ResponseEntity<ItemResponse> createItem(String idempotencyKey, CreateItemRequest request) {
        UUID ownerId = UUID.fromString(
                SecurityContextHolder.getContext().getAuthentication().getName());
        Item item = createItem.create(new CreateItemCommand(ownerId, request.getTitle()));
        return ResponseEntity.created(URI.create("/api/v1/items/" + item.id())).body(toResponse(item));
    }

    private static ItemResponse toResponse(Item item) {
        return new ItemResponse()
                .id(item.id())
                .title(item.title())
                .createdAt(item.createdAt().atOffset(ZoneOffset.UTC))
                .announced(item.announced());
    }
}
