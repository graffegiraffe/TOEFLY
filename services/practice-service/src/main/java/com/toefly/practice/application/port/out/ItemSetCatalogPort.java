package com.toefly.practice.application.port.out;

import com.toefly.practice.application.port.in.ItemSetContent;
import java.util.Optional;
import java.util.UUID;

/**
 * Item sets with keys. The current version comes from content-service (cached for 10 minutes); versions pinned by
 * attempts are kept in practice_db so that running attempts survive content changes (INV-ATT-05).
 */
public interface ItemSetCatalogPort {

    /**
     * The current version from content-service, stored as pinned.
     *
     * @throws ItemSetUnavailableException when content-service does not know the set
     * @throws DependencyUnavailableException when content-service cannot be reached
     */
    ItemSetContent current(UUID itemSetId);

    /** A version pinned by an attempt. */
    Optional<ItemSetContent> pinned(UUID itemSetId, int version);
}
