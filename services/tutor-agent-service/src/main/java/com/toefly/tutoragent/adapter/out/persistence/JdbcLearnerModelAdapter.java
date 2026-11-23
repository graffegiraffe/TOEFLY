package com.toefly.tutoragent.adapter.out.persistence;

import com.toefly.tutoragent.application.port.out.ConcurrencyConflictException;
import com.toefly.tutoragent.application.port.out.LearnerModelRepository;
import com.toefly.tutoragent.domain.learner.LearnerModel;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Learner models as JSON documents with an optimistic version (docs/06 §6.1). */
@Component
public class JdbcLearnerModelAdapter implements LearnerModelRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcLearnerModelAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<LearnerModel> find(UUID userId) {
        return jdbc.sql("SELECT payload FROM learner_model WHERE user_id = :u")
                .param("u", userId)
                .query(String.class)
                .optional()
                .map(payload -> json.readValue(payload, LearnerModel.class));
    }

    @Override
    public void save(LearnerModel model) {
        String payload = json.writeValueAsString(model);
        Timestamp at = Timestamp.from(model.updatedAt() == null ? java.time.Instant.EPOCH : model.updatedAt());
        int changed = model.version() == 1
                ? jdbc.sql("""
                                INSERT INTO learner_model (user_id, version, payload, updated_at)
                                VALUES (:u, :v, :p::jsonb, :at) ON CONFLICT (user_id) DO NOTHING
                                """)
                        .param("u", model.userId())
                        .param("v", model.version())
                        .param("p", payload)
                        .param("at", at)
                        .update()
                : jdbc.sql("""
                                UPDATE learner_model SET version = :v, payload = :p::jsonb, updated_at = :at
                                WHERE user_id = :u AND version = :expected
                                """)
                        .param("u", model.userId())
                        .param("v", model.version())
                        .param("expected", model.version() - 1)
                        .param("p", payload)
                        .param("at", at)
                        .update();
        if (changed != 1) {
            throw new ConcurrencyConflictException("Learner model " + model.userId() + " changed concurrently");
        }
    }

    @Override
    public void snapshot(LearnerModel model, int weekNo) {
        jdbc.sql("""
                        INSERT INTO learner_model_snapshot (user_id, week_no, payload, created_at)
                        VALUES (:u, :w, :p::jsonb, now())
                        ON CONFLICT (user_id, week_no) DO UPDATE SET payload = EXCLUDED.payload, created_at = now()
                        """)
                .param("u", model.userId())
                .param("w", weekNo)
                .param("p", json.writeValueAsString(model))
                .update();
    }

    @Override
    public Optional<LearnerModel> snapshotOf(UUID userId, int weekNo) {
        return jdbc.sql("SELECT payload FROM learner_model_snapshot WHERE user_id = :u AND week_no = :w")
                .param("u", userId)
                .param("w", weekNo)
                .query(String.class)
                .optional()
                .map(payload -> json.readValue(payload, LearnerModel.class));
    }
}
