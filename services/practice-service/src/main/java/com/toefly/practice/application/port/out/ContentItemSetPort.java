package com.toefly.practice.application.port.out;

import java.util.UUID;

/**
 * {@code GET /internal/v1/item-sets/{id}} of content-service: the item set with keys as JSON
 * (docs/05-api-contracts.md §9.3.1). The only synchronous dependency of an attempt start.
 */
public interface ContentItemSetPort {

    /**
     * @throws ItemSetUnavailableException when content-service does not know the set
     * @throws DependencyUnavailableException when content-service cannot be reached
     */
    String fetch(UUID itemSetId);
}
