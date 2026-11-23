package com.toefly.tutoragent.adapter.out.persistence;

import com.toefly.tutoragent.application.port.out.PublicationSchedule;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Debounce windows of learner-model-updated. */
@Component
public class JdbcPublicationScheduleAdapter implements PublicationSchedule {

    private final JdbcClient jdbc;

    public JdbcPublicationScheduleAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void open(UUID userId, Instant dueAt) {
        jdbc.sql("INSERT INTO learner_model_publication (user_id, due_at) VALUES (:u, :d) ON CONFLICT DO NOTHING")
                .param("u", userId)
                .param("d", Timestamp.from(dueAt))
                .update();
    }

    @Override
    public List<UUID> due(Instant now, int limit) {
        return jdbc.sql("SELECT user_id FROM learner_model_publication WHERE due_at <= :now ORDER BY due_at LIMIT :l")
                .param("now", Timestamp.from(now))
                .param("l", limit)
                .query(UUID.class)
                .list();
    }

    @Override
    public void close(UUID userId) {
        jdbc.sql("DELETE FROM learner_model_publication WHERE user_id = :u")
                .param("u", userId)
                .update();
    }
}
