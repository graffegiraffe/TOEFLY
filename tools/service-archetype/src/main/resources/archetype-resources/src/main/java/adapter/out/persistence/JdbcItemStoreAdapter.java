package ${package}.adapter.out.persistence;

import ${package}.application.port.out.ItemStorePort;
import ${package}.domain.item.Item;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcItemStoreAdapter implements ItemStorePort {

    private final JdbcClient jdbc;

    public JdbcItemStoreAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Item item) {
        jdbc.sql("""
                        INSERT INTO item (id, owner_id, title, created_at, announced)
                        VALUES (:id, :ownerId, :title, :createdAt, :announced)
                        ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, announced = EXCLUDED.announced
                        """)
                .param("id", item.id())
                .param("ownerId", item.ownerId())
                .param("title", item.title())
                .param("createdAt", Timestamp.from(item.createdAt()))
                .param("announced", item.announced())
                .update();
    }

    @Override
    public Optional<Item> findById(UUID id) {
        return jdbc.sql("SELECT id, owner_id, title, created_at, announced FROM item WHERE id = :id")
                .param("id", id)
                .query((rs, row) -> new Item(
                        rs.getObject("id", UUID.class),
                        rs.getObject("owner_id", UUID.class),
                        rs.getString("title"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getBoolean("announced")))
                .optional();
    }
}
