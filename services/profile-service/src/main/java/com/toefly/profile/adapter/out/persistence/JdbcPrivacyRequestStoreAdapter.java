package com.toefly.profile.adapter.out.persistence;

import com.toefly.profile.application.port.out.PrivacyRequestStorePort;
import com.toefly.profile.domain.privacy.AccountDeletion;
import com.toefly.profile.domain.privacy.AccountDeletionStatus;
import com.toefly.profile.domain.privacy.DataExport;
import com.toefly.profile.domain.privacy.DataExportStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPrivacyRequestStoreAdapter implements PrivacyRequestStorePort {

    private final JdbcClient jdbc;

    public JdbcPrivacyRequestStoreAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DataExport> latestExport(UUID userId) {
        return jdbc.sql("""
                        SELECT id, user_id, status, requested_at, ready_at FROM data_export
                        WHERE user_id = :userId ORDER BY requested_at DESC LIMIT 1
                        """)
                .param("userId", userId)
                .query((rs, row) -> export(rs))
                .optional();
    }

    @Override
    public Optional<DataExport> findExport(UUID userId, UUID exportId) {
        return jdbc.sql("""
                        SELECT id, user_id, status, requested_at, ready_at FROM data_export
                        WHERE user_id = :userId AND id = :id
                        """)
                .param("userId", userId)
                .param("id", exportId)
                .query((rs, row) -> export(rs))
                .optional();
    }

    @Override
    public void saveExport(DataExport export) {
        jdbc.sql("""
                        INSERT INTO data_export (id, user_id, status, requested_at, ready_at)
                        VALUES (:id, :userId, :status, :requestedAt, :readyAt)
                        ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, ready_at = EXCLUDED.ready_at
                        """)
                .param("id", export.id())
                .param("userId", export.userId())
                .param("status", export.status().name())
                .param("requestedAt", Timestamp.from(export.requestedAt()))
                .param("readyAt", export.readyAt() == null ? null : Timestamp.from(export.readyAt()))
                .update();
    }

    @Override
    public Optional<AccountDeletion> latestDeletion(UUID userId) {
        return jdbc.sql("""
                        SELECT id, user_id, status, requested_at, legal_deadline_at FROM account_deletion
                        WHERE user_id = :userId ORDER BY requested_at DESC LIMIT 1
                        """)
                .param("userId", userId)
                .query((rs, row) -> new AccountDeletion(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        AccountDeletionStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("requested_at").toInstant(),
                        rs.getTimestamp("legal_deadline_at").toInstant()))
                .optional();
    }

    @Override
    public void saveDeletion(AccountDeletion deletion) {
        jdbc.sql("""
                        INSERT INTO account_deletion (id, user_id, status, requested_at, legal_deadline_at)
                        VALUES (:id, :userId, :status, :requestedAt, :deadline)
                        ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status
                        """)
                .param("id", deletion.id())
                .param("userId", deletion.userId())
                .param("status", deletion.status().name())
                .param("requestedAt", Timestamp.from(deletion.requestedAt()))
                .param("deadline", Timestamp.from(deletion.legalDeadlineAt()))
                .update();
    }

    private static DataExport export(ResultSet rs) throws SQLException {
        Timestamp readyAt = rs.getTimestamp("ready_at");
        return new DataExport(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                DataExportStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("requested_at").toInstant(),
                readyAt == null ? null : readyAt.toInstant());
    }
}
