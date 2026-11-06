package com.toefly.content.adapter.out.persistence;

import com.toefly.content.application.port.out.ItemSetStorePort;
import com.toefly.content.domain.catalog.ExamFormat;
import com.toefly.content.domain.catalog.HideReason;
import com.toefly.content.domain.catalog.ItemSet;
import com.toefly.content.domain.catalog.ItemStatus;
import com.toefly.content.domain.catalog.Provenance;
import com.toefly.content.domain.catalog.Section;
import com.toefly.content.domain.catalog.TaskType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcItemSetStoreAdapter implements ItemSetStorePort {

    static final String COLUMNS = """
            id, seed_id, version, section, task_type, exam_format, cefr, topic, domain, skills, source_id,
            source_page, source_item_no, provenance, status, hidden_reason, owner_tutor_id, b_mean, b_basis,
            ets_norm_seconds, estimated_minutes, item_count, payload::text AS payload, audio_state, content_hash,
            search_text, published_at, created_at, updated_at
            """;

    private final JdbcClient jdbc;

    public JdbcItemSetStoreAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ItemSet> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM item_set WHERE id = :id")
                .param("id", id)
                .query(JdbcItemSetStoreAdapter::map)
                .optional();
    }

    @Override
    public List<ItemRow> items(UUID itemSetId) {
        return jdbc.sql(
                        "SELECT id, ordinal, subskill, max_score, b, b_basis FROM item WHERE item_set_id = :id ORDER BY ordinal")
                .param("id", itemSetId)
                .query((rs, n) -> {
                    double b = rs.getDouble("b");
                    return new ItemRow(
                            rs.getObject("id", UUID.class),
                            rs.getInt("ordinal"),
                            rs.getString("subskill"),
                            rs.getInt("max_score"),
                            rs.wasNull() ? null : b,
                            rs.getString("b_basis"));
                })
                .list();
    }

    @Override
    public void insert(ItemSet s, List<ItemRow> items) {
        jdbc.sql("""
                        INSERT INTO item_set (id, seed_id, version, section, task_type, exam_format, cefr, topic, domain,
                            skills, source_id, source_page, source_item_no, provenance, status, hidden_reason,
                            owner_tutor_id, b_mean, b_basis, ets_norm_seconds, estimated_minutes, item_count, payload,
                            audio_state, content_hash, search_text, published_at, created_at, updated_at)
                        VALUES (:id, :seedId, :version, :section, :taskType, :examFormat, :cefr, :topic, :domain,
                            :skills, :sourceId, :sourcePage, :sourceItemNo, :provenance, :status, :hiddenReason,
                            :ownerTutorId, :bMean, :bBasis, :norm, :minutes, :itemCount, CAST(:payload AS jsonb),
                            :audioState, :hash, :searchText, :publishedAt, :createdAt, :updatedAt)
                        """).params(bind(s)).update();
        insertItems(s.id(), items);
    }

    @Override
    public void updateContent(ItemSet s, List<ItemRow> items, @Nullable UUID changedBy) {
        ItemSet old = findById(s.id()).orElseThrow();
        jdbc.sql("""
                        INSERT INTO item_set_revision (item_set_id, version, payload, content_hash, changed_by, created_at)
                        VALUES (:id, :version, CAST(:payload AS jsonb), :hash, :by, :at)
                        ON CONFLICT DO NOTHING
                        """)
                .param("id", old.id())
                .param("version", old.version())
                .param("payload", old.payload())
                .param("hash", old.contentHash())
                .param("by", changedBy)
                .param("at", Timestamp.from(s.updatedAt()))
                .update();
        jdbc.sql("""
                        UPDATE item_set SET version = :version, section = :section, cefr = :cefr, topic = :topic,
                            domain = :domain, skills = :skills, source_page = :sourcePage,
                            source_item_no = :sourceItemNo, ets_norm_seconds = :norm, estimated_minutes = :minutes,
                            item_count = :itemCount, payload = CAST(:payload AS jsonb), content_hash = :hash,
                            search_text = :searchText, updated_at = :updatedAt
                        WHERE id = :id
                        """).params(bind(s)).update();
        jdbc.sql("DELETE FROM item WHERE item_set_id = :id").param("id", s.id()).update();
        insertItems(s.id(), items);
    }

    @Override
    public void saveStatus(ItemSet s) {
        jdbc.sql("""
                        UPDATE item_set SET status = :status, hidden_reason = :hiddenReason,
                            published_at = :publishedAt, updated_at = :updatedAt
                        WHERE id = :id
                        """)
                .param("id", s.id())
                .param("status", s.status().name())
                .param(
                        "hiddenReason",
                        s.hiddenReason() == null ? null : s.hiddenReason().name())
                .param("publishedAt", s.publishedAt() == null ? null : Timestamp.from(s.publishedAt()))
                .param("updatedAt", Timestamp.from(s.updatedAt()))
                .update();
    }

    @Override
    public List<UUID> seedIdsOfSource(UUID sourceId) {
        return jdbc.sql("SELECT id FROM item_set WHERE source_id = :s AND seed_id IS NOT NULL")
                .param("s", sourceId)
                .query(UUID.class)
                .list();
    }

    @Override
    public List<UUID> idsOfSource(UUID sourceId) {
        return jdbc.sql("SELECT id FROM item_set WHERE source_id = :s")
                .param("s", sourceId)
                .query(UUID.class)
                .list();
    }

    private void insertItems(UUID itemSetId, List<ItemRow> items) {
        for (ItemRow i : items) {
            jdbc.sql("""
                            INSERT INTO item (id, item_set_id, ordinal, subskill, max_score, b, b_basis)
                            VALUES (:id, :set, :ordinal, :subskill, :max, :b, :basis)
                            """)
                    .param("id", i.id())
                    .param("set", itemSetId)
                    .param("ordinal", i.ordinal())
                    .param("subskill", i.subskill())
                    .param("max", i.maxScore())
                    .param("b", i.b())
                    .param("basis", i.bBasis())
                    .update();
        }
    }

    private static java.util.Map<String, Object> bind(ItemSet s) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("id", s.id());
        m.put("seedId", s.seedId());
        m.put("version", s.version());
        m.put("section", s.section() == null ? null : s.section().name());
        m.put("taskType", s.taskType().name());
        m.put("examFormat", s.examFormat().name());
        m.put("cefr", s.cefr());
        m.put("topic", s.topic());
        m.put("domain", s.domain());
        m.put("skills", s.skills().toArray(String[]::new));
        m.put("sourceId", s.sourceId());
        m.put("sourcePage", s.sourcePage());
        m.put("sourceItemNo", s.sourceItemNo());
        m.put("provenance", s.provenance().name());
        m.put("status", s.status().name());
        m.put("hiddenReason", s.hiddenReason() == null ? null : s.hiddenReason().name());
        m.put("ownerTutorId", s.ownerTutorId());
        m.put("bMean", s.bMean());
        m.put("bBasis", s.bBasis());
        m.put("norm", s.etsNormSeconds());
        m.put("minutes", s.estimatedMinutes());
        m.put("itemCount", s.itemCount());
        m.put("payload", s.payload());
        m.put("audioState", s.audioState());
        m.put("hash", s.contentHash());
        m.put("searchText", s.searchText());
        m.put("publishedAt", s.publishedAt() == null ? null : Timestamp.from(s.publishedAt()));
        m.put("createdAt", Timestamp.from(s.createdAt()));
        m.put("updatedAt", Timestamp.from(s.updatedAt()));
        return m;
    }

    static ItemSet map(ResultSet rs, int row) throws SQLException {
        String section = rs.getString("section");
        String hidden = rs.getString("hidden_reason");
        Timestamp published = rs.getTimestamp("published_at");
        int page = rs.getInt("source_page");
        boolean noPage = rs.wasNull();
        return new ItemSet(
                rs.getObject("id", UUID.class),
                rs.getString("seed_id"),
                rs.getInt("version"),
                section == null ? null : Section.valueOf(section),
                TaskType.valueOf(rs.getString("task_type")),
                ExamFormat.valueOf(rs.getString("exam_format")),
                rs.getString("cefr"),
                rs.getString("topic"),
                rs.getString("domain"),
                Arrays.asList((String[]) rs.getArray("skills").getArray()),
                rs.getObject("source_id", UUID.class),
                noPage ? null : page,
                rs.getString("source_item_no"),
                Provenance.valueOf(rs.getString("provenance")),
                ItemStatus.valueOf(rs.getString("status")),
                hidden == null ? null : HideReason.valueOf(hidden),
                rs.getObject("owner_tutor_id", UUID.class),
                rs.getDouble("b_mean"),
                rs.getString("b_basis"),
                rs.getInt("ets_norm_seconds"),
                rs.getDouble("estimated_minutes"),
                rs.getInt("item_count"),
                rs.getString("payload"),
                rs.getString("audio_state"),
                rs.getString("content_hash").strip(),
                rs.getString("search_text"),
                published == null ? null : published.toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
