package com.toefly.tutoragent.adapter.out.persistence;

import com.toefly.tutoragent.application.port.out.PracticeResultRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Results of attempts and evaluated work. */
@Component
public class JdbcPracticeResultAdapter implements PracticeResultRepository {

    private final JdbcClient jdbc;

    public JdbcPracticeResultAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Result r) {
        jdbc.sql("""
                        INSERT INTO practice_result (source_id, user_id, task_type, accuracy, score, occurred_at)
                        VALUES (:s, :u, :t, :a, :sc, :at)
                        ON CONFLICT (source_id) DO UPDATE SET accuracy = EXCLUDED.accuracy, score = EXCLUDED.score,
                            occurred_at = EXCLUDED.occurred_at
                        """)
                .param("s", r.sourceId())
                .param("u", r.userId())
                .param("t", r.taskType())
                .param("a", r.accuracy())
                .param("sc", r.score())
                .param("at", Timestamp.from(r.occurredAt()))
                .update();
    }

    @Override
    public List<Result> between(UUID userId, Instant from, Instant to) {
        return jdbc.sql("""
                        SELECT * FROM practice_result WHERE user_id = :u AND occurred_at >= :f AND occurred_at <= :t
                        """)
                .param("u", userId)
                .param("f", Timestamp.from(from))
                .param("t", Timestamp.from(to))
                .query((rs, n) -> new Result(
                        rs.getObject("source_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("task_type"),
                        rs.getObject("accuracy", Double.class),
                        rs.getObject("score", Double.class),
                        rs.getTimestamp("occurred_at").toInstant()))
                .list();
    }
}
