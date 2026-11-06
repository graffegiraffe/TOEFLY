package com.toefly.content.adapter.in.web;

import com.toefly.content.adapter.in.web.api.InternalApi;
import com.toefly.content.adapter.in.web.dto.ItemSet;
import com.toefly.content.application.port.in.BrowseCatalogUseCase;
import com.toefly.content.domain.catalog.ItemSetNotFoundException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service view with answer keys, for practice-service (docs/04-architecture.md §3.4). */
@RestController
public class InternalController implements InternalApi {

    private final BrowseCatalogUseCase catalog;

    public InternalController(BrowseCatalogUseCase catalog) {
        this.catalog = catalog;
    }

    @Override
    public ResponseEntity<ItemSet> internalGetItemSet(UUID itemSetId) {
        var set = catalog.any(itemSetId).orElseThrow(() -> new ItemSetNotFoundException(itemSetId));
        return ResponseEntity.ok(CatalogMapper.full(set, catalog.source(set.sourceId()), true));
    }
}
