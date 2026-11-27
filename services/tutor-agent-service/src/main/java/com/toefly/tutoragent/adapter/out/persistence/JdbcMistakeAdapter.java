package com.toefly.tutoragent.adapter.out.persistence;

import com.toefly.tutoragent.application.port.out.MistakeRepository;
import com.toefly.tutoragent.domain.learner.MistakeRecord;
import com.toefly.tutoragent.domain.learner.Opportunity;
import com.toefly.tutoragent.domain.learner.Severity;
import com.toefly.tutoragent.domain.learner.SourceType;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Mistake records and opportunities (docs/06 §6.1). */
@Component
public class JdbcMistakeAdapter implements MistakeRepository {

    private final JdbcClient jdbc;

    public JdbcMistakeAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(UUID userId, Collection<MistakeRecord> records, Collection<Opportunity> opportunities) {
        for (MistakeRecord r : records) {
            jdbc.sql("""
                            INSERT INTO mistake_record (id, user_id, category, subcategory, source_type, source_id, item_id,
                                task_type, excerpt, correction, explanation, severity, unit_refs, created_at)
                            VALUES (:id, :u, :c, :s, :st, :src, :item, :tt, :ex, :cor, :expl, :sev, :units, :at)
                            """)
                    .param("id", r.id())
                    .param("u", r.userId())
                    .param("c", r.category().name())
                    .param("s", r.subcategory())
                    .param("st", r.sourceType().name())
                    .param("src", r.sourceId())
                    .param("item", r.itemId())
                    .param("tt", r.taskType())
                    .param("ex", r.excerpt())
                    .param("cor", r.correction())
                    .param("expl", r.explanation())
                    .param("sev", r.severity().name())
                    .param("units", r.unitRefs().toArray(String[]::new))
                    .param("at", Timestamp.from(r.createdAt()))
                    .update();
        }
        for (Opportunity o : opportunities) {
            jdbc.sql("""
                            INSERT INTO mistake_opportunity (user_id, code, source_id, item_id, occurred_at)
                            VALUES (:u, :c, :src, :item, :at)
                            """)
                    .param("u", userId)
                    .param("c", o.code())
                    .param("src", o.sourceId())
                    .param("item", o.itemId())
                    .param("at", Timestamp.from(o.occurredAt()))
                    .update();
        }
    }

    @Override
    public List<MistakeRecord> records(UUID userId, Instant since) {
        return jdbc.sql("SELECT * FROM mistake_record WHERE user_id = :u AND created_at >= :since")
                .param("u", userId)
                .param("since", Timestamp.from(since))
                .query((rs, n) -> record(rs))
                .list();
    }

    @Override
    public List<Opportunity> opportunities(UUID userId, Instant since) {
        return jdbc.sql("""
                        SELECT code, source_id, item_id, occurred_at FROM mistake_opportunity
                        WHERE user_id = :u AND occurred_at >= :since
                        """)
                .param("u", userId)
                .param("since", Timestamp.from(since))
                .query((rs, n) -> new Opportunity(
                        rs.getString("code"),
                        rs.getObject("source_id", UUID.class),
                        rs.getObject("item_id", UUID.class),
                        rs.getTimestamp("occurred_at").toInstant()))
                .list();
    }

    @Override
    public List<MistakeRecord> examples(UUID userId, String subcategory, Instant since, int limit) {
        return jdbc.sql("""
                        SELECT * FROM mistake_record
                        WHERE user_id = :u AND subcategory = :s AND created_at >= :since AND excerpt IS NOT NULL
                        ORDER BY (severity = 'CRITICAL') DESC, created_at DESC LIMIT :limit
                        """)
                .param("u", userId)
                .param("s", subcategory)
                .param("since", Timestamp.from(since))
                .param("limit", limit)
                .query((rs, n) -> record(rs))
                .list();
    }

    static MistakeRecord record(ResultSet rs) throws SQLException {
        Array units = rs.getArray("unit_refs");
        return new MistakeRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("subcategory"),
                SourceType.valueOf(rs.getString("source_type")),
                rs.getObject("source_id", UUID.class),
                rs.getObject("item_id", UUID.class),
                rs.getString("task_type"),
                rs.getString("excerpt"),
                rs.getString("correction"),
                rs.getString("explanation"),
                Severity.valueOf(rs.getString("severity")),
                units == null ? List.of() : Arrays.asList((String[]) units.getArray()),
                rs.getTimestamp("created_at").toInstant());
    }
}
