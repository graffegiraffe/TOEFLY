package com.toefly.practice.adapter.out.persistence;

import com.toefly.practice.application.port.out.ItemPoolPort;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** item_pool and item_difficulty: the published sets as content-service announced them. */
@Repository
public class JdbcItemPoolAdapter implements ItemPoolPort {

    private final JdbcClient jdbc;

    public JdbcItemPoolAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PoolEntry> find(UUID itemSetId) {
        return jdbc.sql("SELECT version, available FROM item_pool WHERE item_set_id = :id")
                .param("id", itemSetId)
                .query((rs, row) -> new PoolEntry(itemSetId, rs.getInt(1), rs.getBoolean(2), items(itemSetId)))
                .optional();
    }

    private List<PoolItem> items(UUID itemSetId) {
        return jdbc.sql("SELECT item_id, ordinal, b FROM item_difficulty WHERE item_set_id = :id ORDER BY ordinal")
                .param("id", itemSetId)
                .query((rs, row) -> {
                    BigDecimal b = rs.getBigDecimal(3);
                    return new PoolItem(rs.getObject(1, UUID.class), rs.getInt(2), b == null ? null : b.doubleValue());
                })
                .list();
    }

    @Override
    public void published(UUID itemSetId, int version, String examFormat, List<PoolItem> items) {
        jdbc.sql("""
                        INSERT INTO item_pool (item_set_id, version, exam_format, available, updated_at)
                        VALUES (:id, :version, :format, true, now())
                        ON CONFLICT (item_set_id) DO UPDATE SET version = GREATEST(item_pool.version, EXCLUDED.version),
                            exam_format = EXCLUDED.exam_format, available = true, updated_at = now()
                        """)
                .param("id", itemSetId)
                .param("version", version)
                .param("format", examFormat)
                .update();
        for (PoolItem item : items) {
            jdbc.sql("""
                            INSERT INTO item_difficulty (item_id, item_set_id, ordinal, b)
                            VALUES (:item, :set, :ordinal, :b)
                            ON CONFLICT (item_id) DO UPDATE SET ordinal = EXCLUDED.ordinal, b = EXCLUDED.b
                            """)
                    .param("item", item.itemId())
                    .param("set", itemSetId)
                    .param("ordinal", item.ordinal())
                    .param("b", item.b())
                    .update();
        }
    }

    @Override
    public void hidden(UUID itemSetId) {
        jdbc.sql("UPDATE item_pool SET available = false, updated_at = now() WHERE item_set_id = :id")
                .param("id", itemSetId)
                .update();
    }
}
