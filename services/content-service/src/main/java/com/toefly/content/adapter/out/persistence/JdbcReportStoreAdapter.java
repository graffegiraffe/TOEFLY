package com.toefly.content.adapter.out.persistence;

import com.toefly.content.application.port.out.ReportStorePort;
import com.toefly.content.domain.report.ItemReport;
import com.toefly.content.domain.report.ReportReason;
import com.toefly.content.domain.report.ReportStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReportStoreAdapter implements ReportStorePort {

    private static final String COLUMNS =
            "id, item_set_id, item_ordinal, reporter_id, reason, comment, status, resolved_by, created_at, resolved_at";

    private final JdbcClient jdbc;

    public JdbcReportStoreAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insert(ItemReport r) {
        return jdbc.sql("""
                        INSERT INTO item_report (id, item_set_id, item_ordinal, reporter_id, reason, comment, status, created_at)
                        VALUES (:id, :set, :ordinal, :reporter, :reason, :comment, 'OPEN', :at)
                        ON CONFLICT DO NOTHING
                        """)
                .param("id", r.id())
                .param("set", r.itemSetId())
                .param("ordinal", r.itemOrdinal())
                .param("reporter", r.reporterId())
                .param("reason", r.reason().name())
                .param("comment", r.comment())
                .param("at", Timestamp.from(r.createdAt()))
                .update() == 1;
    }

    @Override
    public Optional<ItemReport> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM item_report WHERE id = :id")
                .param("id", id)
                .query(JdbcReportStoreAdapter::map)
                .optional();
    }

    @Override
    public void update(ItemReport r) {
        jdbc.sql("UPDATE item_report SET status = :status, resolved_by = :by, resolved_at = :at WHERE id = :id")
                .param("id", r.id())
                .param("status", r.status().name())
                .param("by", r.resolvedBy())
                .param("at", r.resolvedAt() == null ? null : Timestamp.from(r.resolvedAt()))
                .update();
    }

    @Override
    public List<ItemReport> list(@Nullable ReportStatus status, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM item_report WHERE (:status IS NULL OR status = :status)"
                        + " ORDER BY created_at, id LIMIT :limit")
                .param("status", status == null ? null : status.name())
                .param("limit", limit)
                .query(JdbcReportStoreAdapter::map)
                .list();
    }

    private static ItemReport map(ResultSet rs, int n) throws SQLException {
        int ordinal = rs.getInt("item_ordinal");
        boolean noOrdinal = rs.wasNull();
        Timestamp resolved = rs.getTimestamp("resolved_at");
        return new ItemReport(rs.getObject("id", UUID.class), rs.getObject("item_set_id", UUID.class),
                noOrdinal ? null : ordinal, rs.getObject("reporter_id", UUID.class),
                ReportReason.valueOf(rs.getString("reason")), rs.getString("comment"),
                ReportStatus.valueOf(rs.getString("status")), rs.getObject("resolved_by", UUID.class),
                rs.getTimestamp("created_at").toInstant(), resolved == null ? null : resolved.toInstant());
    }
}
