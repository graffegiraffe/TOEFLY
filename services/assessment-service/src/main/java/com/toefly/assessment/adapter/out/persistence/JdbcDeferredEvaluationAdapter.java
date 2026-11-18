package com.toefly.assessment.adapter.out.persistence;

import com.toefly.assessment.application.port.out.DeferredEvaluationRepository;
import com.toefly.assessment.domain.submission.DeferredEvaluation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@code deferred_evaluation}; due rows are taken with {@code FOR UPDATE SKIP LOCKED}. */
@Repository
public class JdbcDeferredEvaluationAdapter implements DeferredEvaluationRepository {

    private final JdbcClient jdbc;

    public JdbcDeferredEvaluationAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(DeferredEvaluation d) {
        jdbc.sql("""
                        INSERT INTO deferred_evaluation (submission_id, version_no, user_id, attempts, deferred_at,
                            next_attempt_at, last_error, dead_lettered)
                        VALUES (:s, :v, :user, :attempts, :deferred, :next, :error, :dead)
                        ON CONFLICT (submission_id, version_no) DO UPDATE SET attempts = EXCLUDED.attempts,
                            next_attempt_at = EXCLUDED.next_attempt_at, last_error = EXCLUDED.last_error,
                            dead_lettered = EXCLUDED.dead_lettered
                        """)
                .param("s", d.submissionId())
                .param("v", d.versionNo())
                .param("user", d.userId())
                .param("attempts", d.attempts())
                .param("deferred", Timestamp.from(d.deferredAt()))
                .param("next", Timestamp.from(d.nextAttemptAt()))
                .param("error", d.lastError())
                .param("dead", d.deadLettered())
                .update();
    }

    @Override
    public Optional<DeferredEvaluation> find(UUID submissionId, int versionNo) {
        return jdbc.sql("SELECT * FROM deferred_evaluation WHERE submission_id = :s AND version_no = :v")
                .param("s", submissionId)
                .param("v", versionNo)
                .query((rs, n) -> row(rs))
                .optional();
    }

    @Override
    public void delete(UUID submissionId, int versionNo) {
        jdbc.sql("DELETE FROM deferred_evaluation WHERE submission_id = :s AND version_no = :v")
                .param("s", submissionId)
                .param("v", versionNo)
                .update();
    }

    @Override
    public List<DeferredEvaluation> lockDue(Instant now, int limit) {
        return jdbc.sql("""
                        SELECT * FROM deferred_evaluation WHERE next_attempt_at <= :now
                        ORDER BY next_attempt_at LIMIT :limit FOR UPDATE SKIP LOCKED
                        """)
                .param("now", Timestamp.from(now))
                .param("limit", limit)
                .query((rs, n) -> row(rs))
                .list();
    }

    @Override
    public List<DeferredEvaluation> page(int offset, int limit) {
        return jdbc.sql("SELECT * FROM deferred_evaluation ORDER BY deferred_at, submission_id LIMIT :l OFFSET :o")
                .param("l", limit)
                .param("o", offset)
                .query((rs, n) -> row(rs))
                .list();
    }

    private static DeferredEvaluation row(ResultSet rs) throws SQLException {
        return new DeferredEvaluation(
                rs.getObject("submission_id", UUID.class),
                rs.getInt("version_no"),
                rs.getObject("user_id", UUID.class),
                rs.getInt("attempts"),
                rs.getTimestamp("deferred_at").toInstant(),
                rs.getTimestamp("next_attempt_at").toInstant(),
                rs.getString("last_error"),
                rs.getBoolean("dead_lettered"));
    }
}
