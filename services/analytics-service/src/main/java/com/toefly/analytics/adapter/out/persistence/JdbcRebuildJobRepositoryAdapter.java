package com.toefly.analytics.adapter.out.persistence;

import com.toefly.analytics.application.port.in.ProjectionName;
import com.toefly.analytics.application.port.in.RebuildJob;
import com.toefly.analytics.application.port.out.RebuildJobRepository;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRebuildJobRepositoryAdapter implements RebuildJobRepository {

    private final JdbcClient jdbc;

    JdbcRebuildJobRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean start(RebuildJob job, UUID idempotencyKey) {
        return jdbc.sql("""
                        INSERT INTO projection_rebuild_job (id, projection, status, requested_by, requested_at,
                                                            idempotency_key)
                        VALUES (:id, :projection, :status, :by, :at, :key)
                        ON CONFLICT DO NOTHING
                        """)
                        .param("key", idempotencyKey)
                        .param("id", job.id())
                        .param("projection", job.projection().name())
                        .param("status", job.status().name())
                        .param("by", job.requestedBy())
                        .param("at", Timestamp.from(job.requestedAt()))
                        .update()
                == 1;
    }

    @Override
    public void update(RebuildJob job) {
        jdbc.sql("""
                        UPDATE projection_rebuild_job
                        SET status = :status, finished_at = :finished, events_replayed = :events, error = :error
                        WHERE id = :id
                        """)
                .param("id", job.id())
                .param("status", job.status().name())
                .param("finished", job.finishedAt() == null ? null : Timestamp.from(job.finishedAt()))
                .param("events", job.eventsReplayed())
                .param("error", job.error())
                .update();
    }

    @Override
    public Optional<RebuildJob> find(UUID id) {
        return jdbc.sql("SELECT * FROM projection_rebuild_job WHERE id = :id")
                .param("id", id)
                .query(JdbcRebuildJobRepositoryAdapter::job)
                .optional();
    }

    @Override
    public Optional<RebuildJob> findByKey(UUID adminId, UUID idempotencyKey) {
        return jdbc.sql("SELECT * FROM projection_rebuild_job WHERE requested_by = :by AND idempotency_key = :key")
                .param("by", adminId)
                .param("key", idempotencyKey)
                .query(JdbcRebuildJobRepositoryAdapter::job)
                .optional();
    }

    private static RebuildJob job(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        Timestamp finished = rs.getTimestamp("finished_at");
        return new RebuildJob(
                rs.getObject("id", UUID.class),
                ProjectionName.valueOf(rs.getString("projection")),
                RebuildJob.Status.valueOf(rs.getString("status")),
                rs.getObject("requested_by", UUID.class),
                rs.getTimestamp("requested_at").toInstant(),
                finished == null ? null : finished.toInstant(),
                rs.getLong("events_replayed"),
                rs.getString("error"));
    }

    @Override
    public void failRunning(String reason) {
        jdbc.sql("""
                        UPDATE projection_rebuild_job SET status = 'FAILED', finished_at = now(), error = :reason
                        WHERE status = 'RUNNING'
                        """).param("reason", reason).update();
    }
}
