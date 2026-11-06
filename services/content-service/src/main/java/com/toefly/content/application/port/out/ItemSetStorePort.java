package com.toefly.content.application.port.out;

import com.toefly.content.domain.catalog.ItemSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface ItemSetStorePort {

    /** One row of {@code item}. */
    record ItemRow(UUID id, int ordinal, @Nullable String subskill, int maxScore, @Nullable Double b, String bBasis) {}

    Optional<ItemSet> findById(UUID id);

    List<ItemRow> items(UUID itemSetId);

    void insert(ItemSet itemSet, List<ItemRow> items);

    /** Replaces content fields and items, bumps the version and keeps the previous payload as a revision. */
    void updateContent(ItemSet updated, List<ItemRow> items, @Nullable UUID changedBy);

    /** Persists status, hidden reason and publication time. */
    void saveStatus(ItemSet itemSet);

    /** Ids of all item sets of a source that came from the seed. */
    List<UUID> seedIdsOfSource(UUID sourceId);

    /** Ids of all item sets of a source in the given status. */
    List<UUID> idsOfSource(UUID sourceId);
}
