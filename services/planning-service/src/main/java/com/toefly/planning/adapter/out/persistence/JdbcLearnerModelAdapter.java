package com.toefly.planning.adapter.out.persistence;

import com.toefly.planning.application.port.out.LearnerModelPort;
import com.toefly.planning.domain.learner.LearnerModelView;
import com.toefly.planning.domain.learner.MistakeStat;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** {@code learner_model_view}: the statistics as jsonb, the newer version of the model wins. */
@Repository
public class JdbcLearnerModelAdapter implements LearnerModelPort {

    private static final TypeReference<List<MistakeStat>> STATS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Double>> BANDS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcLearnerModelAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void save(LearnerModelView view) {
        jdbc.sql("""
                        INSERT INTO learner_model_view (user_id, version, updated_at, mistake_stats, bands, received_at)
                        VALUES (:user, :version, :updated, CAST(:stats AS jsonb), CAST(:bands AS jsonb), now())
                        ON CONFLICT (user_id) DO UPDATE SET version = EXCLUDED.version,
                            updated_at = EXCLUDED.updated_at, mistake_stats = EXCLUDED.mistake_stats,
                            bands = EXCLUDED.bands, received_at = EXCLUDED.received_at
                        WHERE learner_model_view.version < EXCLUDED.version
                        """)
                .param("user", view.userId())
                .param("version", view.version())
                .param("updated", Timestamp.from(view.updatedAt()))
                .param("stats", json.writeValueAsString(view.mistakes()))
                .param("bands", json.writeValueAsString(view.bands()))
                .update();
    }

    @Override
    public Optional<LearnerModelView> find(UUID userId) {
        return jdbc.sql("SELECT * FROM learner_model_view WHERE user_id = :user")
                .param("user", userId)
                .query((rs, row) -> new LearnerModelView(
                        rs.getObject("user_id", UUID.class),
                        rs.getLong("version"),
                        rs.getTimestamp("updated_at").toInstant(),
                        json.readValue(rs.getString("mistake_stats"), STATS),
                        json.readValue(rs.getString("bands"), BANDS)))
                .optional();
    }

    @Override
    public void deleteByUser(UUID userId) {
        jdbc.sql("DELETE FROM learner_model_view WHERE user_id = :user")
                .param("user", userId)
                .update();
    }
}
