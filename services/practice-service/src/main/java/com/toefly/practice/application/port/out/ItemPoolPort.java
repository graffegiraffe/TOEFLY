package com.toefly.practice.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Local projection of published sets and their item difficulties (item_pool, item_difficulty), fed by
 * {@code item-set-published} and {@code item-set-hidden} (docs/03-domain-model.md §6.5, INV-ATT-06).
 */
public interface ItemPoolPort {

    Optional<PoolEntry> find(UUID itemSetId);

    void published(UUID itemSetId, int version, String examFormat, List<PoolItem> items);

    void hidden(UUID itemSetId);

    record PoolEntry(UUID itemSetId, int version, boolean available, List<PoolItem> items) {}

    record PoolItem(UUID itemId, int ordinal, @Nullable Double b) {}
}
