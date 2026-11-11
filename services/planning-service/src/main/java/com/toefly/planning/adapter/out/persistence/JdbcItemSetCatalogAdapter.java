package com.toefly.planning.adapter.out.persistence;

import com.toefly.planning.application.port.out.ItemSetCatalogPort;
import com.toefly.planning.domain.week.TaskType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** A newer version replaces the row; an event older than the row changes nothing. */
@Repository
public class JdbcItemSetCatalogAdapter implements ItemSetCatalogPort {

    private final JdbcClient jdbc;

    public JdbcItemSetCatalogAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void published(CatalogEntry entry, Instant occurredAt) {
        jdbc.sql("""
                        INSERT INTO item_set_catalog (item_set_id, version, task_type, exam_format, difficulty_b,
                            estimated_minutes, topic, owner_tutor_id, hidden, updated_at)
                        VALUES (:id, :version, :type, :format, :b, :minutes, :topic, :owner, false, :at)
                        ON CONFLICT (item_set_id) DO UPDATE SET version = EXCLUDED.version,
                            task_type = EXCLUDED.task_type, exam_format = EXCLUDED.exam_format,
                            difficulty_b = EXCLUDED.difficulty_b, estimated_minutes = EXCLUDED.estimated_minutes,
                            topic = EXCLUDED.topic, owner_tutor_id = EXCLUDED.owner_tutor_id, hidden = false,
                            updated_at = EXCLUDED.updated_at
                        WHERE item_set_catalog.version <= EXCLUDED.version
                        """)
                .param("id", entry.itemSetId())
                .param("version", entry.version())
                .param("type", entry.taskType().name())
                .param("format", entry.examFormat())
                .param("b", entry.difficultyB())
                .param("minutes", entry.estimatedMinutes())
                .param("topic", entry.topic().length() > 300 ? entry.topic().substring(0, 300) : entry.topic())
                .param("owner", entry.ownerTutorId())
                .param("at", Timestamp.from(occurredAt))
                .update();
    }

    @Override
    public void hidden(UUID itemSetId, Instant occurredAt) {
        jdbc.sql("UPDATE item_set_catalog SET hidden = true, updated_at = :at WHERE item_set_id = :id")
                .param("id", itemSetId)
                .param("at", Timestamp.from(occurredAt))
                .update();
    }

    @Override
    public List<CatalogEntry> visible(TaskType type) {
        return jdbc.sql("""
                        SELECT * FROM item_set_catalog WHERE task_type = :type AND NOT hidden ORDER BY item_set_id
                        """)
                .param("type", type.name())
                .query((rs, row) -> new CatalogEntry(
                        rs.getObject("item_set_id", UUID.class),
                        rs.getInt("version"),
                        TaskType.valueOf(rs.getString("task_type")),
                        rs.getString("exam_format"),
                        rs.getDouble("difficulty_b"),
                        rs.getBigDecimal("estimated_minutes").doubleValue(),
                        rs.getString("topic"),
                        rs.getObject("owner_tutor_id", UUID.class)))
                .list();
    }
}
