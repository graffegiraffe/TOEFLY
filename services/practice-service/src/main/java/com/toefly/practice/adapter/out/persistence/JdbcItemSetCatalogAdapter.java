package com.toefly.practice.adapter.out.persistence;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.toefly.practice.application.port.in.ItemSetContent;
import com.toefly.practice.application.port.out.ContentItemSetPort;
import com.toefly.practice.application.port.out.ItemSetCatalogPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Pins the item set versions used by attempts in item_set_snapshot, so that scoring and reviews never depend on
 * the current state of content-service (INV-ATT-05).
 */
@Repository
public class JdbcItemSetCatalogAdapter implements ItemSetCatalogPort {

    private final JdbcClient jdbc;
    private final ContentItemSetPort content;
    private final Cache<String, ItemSetContent> parsed =
            Caffeine.newBuilder().maximumSize(500).build();

    public JdbcItemSetCatalogAdapter(JdbcClient jdbc, ContentItemSetPort content) {
        this.jdbc = jdbc;
        this.content = content;
    }

    @Override
    public ItemSetContent current(UUID itemSetId) {
        String json = content.fetch(itemSetId);
        ItemSetContent current = ItemSetPayloadParser.parse(json);
        jdbc.sql("""
                        INSERT INTO item_set_snapshot (item_set_id, version, payload)
                        VALUES (:id, :version, :payload::jsonb) ON CONFLICT DO NOTHING
                        """)
                .param("id", itemSetId)
                .param("version", current.version())
                .param("payload", json)
                .update();
        return current;
    }

    @Override
    public Optional<ItemSetContent> pinned(UUID itemSetId, int version) {
        String key = itemSetId + "@" + version;
        ItemSetContent cached = parsed.getIfPresent(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<ItemSetContent> found = jdbc.sql(
                        "SELECT payload::text FROM item_set_snapshot WHERE item_set_id = :id AND version = :version")
                .param("id", itemSetId)
                .param("version", version)
                .query(String.class)
                .optional()
                .map(ItemSetPayloadParser::parse);
        found.ifPresent(value -> parsed.put(key, value));
        return found;
    }
}
