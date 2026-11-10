package com.toefly.practice.adapter.out.persistence;

import com.toefly.practice.application.port.out.IdempotencyStorePort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** idempotency_record of toefly-common-outbox (docs/05-api-contracts.md §2.6). */
@Repository
public class JdbcIdempotencyStoreAdapter implements IdempotencyStorePort {

    private final JdbcClient jdbc;

    public JdbcIdempotencyStoreAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean claim(UUID userId, String key, String operation, String requestHash, Instant expiresAt) {
        jdbc.sql(
                        "DELETE FROM idempotency_record WHERE user_id = :user AND idempotency_key = :key AND expires_at < now()")
                .param("user", userId)
                .param("key", key)
                .update();
        return jdbc.sql("""
                        INSERT INTO idempotency_record (idempotency_key, user_id, operation, request_hash, status,
                            expires_at)
                        VALUES (:key, :user, :operation, :hash, 'IN_PROGRESS', :expires)
                        ON CONFLICT (user_id, idempotency_key) DO NOTHING
                        """)
                        .param("key", key)
                        .param("user", userId)
                        .param("operation", operation)
                        .param("hash", requestHash)
                        .param("expires", Timestamp.from(expiresAt))
                        .update()
                == 1;
    }

    @Override
    public Optional<Record> find(UUID userId, String key) {
        return jdbc.sql("""
                        SELECT operation, request_hash, status, response_status, response_body::text, resource_uri
                        FROM idempotency_record WHERE user_id = :user AND idempotency_key = :key
                        """)
                .param("user", userId)
                .param("key", key)
                .query((rs, row) -> {
                    int status = rs.getInt(4);
                    Integer responseStatus = rs.wasNull() ? null : status;
                    return new Record(
                            rs.getString(1),
                            rs.getString(2).strip(),
                            "COMPLETED".equals(rs.getString(3)),
                            responseStatus,
                            rs.getString(5),
                            rs.getString(6));
                })
                .optional();
    }

    @Override
    public void complete(UUID userId, String key, int status, String body, @Nullable String resourceUri) {
        jdbc.sql("""
                        UPDATE idempotency_record SET status = 'COMPLETED', response_status = :status,
                            response_body = :body::jsonb, resource_uri = :uri
                        WHERE user_id = :user AND idempotency_key = :key
                        """)
                .param("status", status)
                .param("body", body)
                .param("uri", resourceUri)
                .param("user", userId)
                .param("key", key)
                .update();
    }

    @Override
    public void release(UUID userId, String key) {
        jdbc.sql(
                        "DELETE FROM idempotency_record WHERE user_id = :user AND idempotency_key = :key AND status <> 'COMPLETED'")
                .param("user", userId)
                .param("key", key)
                .update();
    }
}
