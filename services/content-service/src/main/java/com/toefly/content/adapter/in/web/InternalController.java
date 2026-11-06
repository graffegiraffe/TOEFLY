package com.toefly.content.adapter.in.web;

import com.toefly.content.adapter.in.web.api.InternalApi;
import com.toefly.content.adapter.in.web.dto.ItemSet;
import com.toefly.content.application.service.CatalogQueryService;
import com.toefly.content.application.service.ModerationService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service view with answer keys, for practice-service (docs/04-architecture.md §3.4). */
@RestController
public class InternalController implements InternalApi {

    private final CatalogQueryService catalog;

    public InternalController(CatalogQueryService catalog) {
        this.catalog = catalog;
    }

    @Override
    public ResponseEntity<ItemSet> internalGetItemSet(UUID itemSetId) {
        var set = catalog.any(itemSetId).orElseThrow(() -> new ModerationService.ItemSetNotFoundException(itemSetId));
        return ResponseEntity.ok(CatalogMapper.full(set, catalog.source(set.sourceId()), true));
    }
}
