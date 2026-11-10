package com.toefly.practice.adapter.out.persistence;

import com.toefly.practice.application.port.in.AbilityChange;
import com.toefly.practice.application.port.out.AbilityStorePort;
import com.toefly.practice.application.port.out.ConcurrencyConflictException;
import com.toefly.practice.domain.ability.AbilityEstimate;
import com.toefly.practice.domain.ability.Response;
import com.toefly.practice.domain.attempt.Section;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** ability_estimate (versioned), ability_history (one row per attempt) and ability_response. */
@Repository
public class JdbcAbilityStoreAdapter implements AbilityStorePort {

    private final JdbcClient jdbc;

    public JdbcAbilityStoreAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AbilityEstimate> find(UUID userId, Section section) {
        return jdbc.sql("SELECT * FROM ability_estimate WHERE user_id = :user AND section = :section")
                .param("user", userId)
                .param("section", section.name())
                .query(JdbcAbilityStoreAdapter::map)
                .optional();
    }

    @Override
    public List<AbilityEstimate> findAll(UUID userId) {
        return jdbc.sql("SELECT * FROM ability_estimate WHERE user_id = :user ORDER BY section")
                .param("user", userId)
                .query(JdbcAbilityStoreAdapter::map)
                .list();
    }

    @Override
    public void save(AbilityEstimate estimate) {
        Instant updated = estimate.updatedAt() == null ? Instant.now() : estimate.updatedAt();
        int changed;
        if (estimate.version() == 0) {
            changed = jdbc.sql("""
                            INSERT INTO ability_estimate (user_id, section, theta, info, n, version, updated_at)
                            VALUES (:user, :section, :theta, :info, :n, 1, :at)
                            ON CONFLICT (user_id, section) DO NOTHING
                            """).params(params(estimate, updated)).update();
        } else {
            changed = jdbc.sql("""
                            UPDATE ability_estimate SET theta = :theta, info = :info, n = :n, version = version + 1,
                                updated_at = :at
                            WHERE user_id = :user AND section = :section AND version = :version
                            """)
                    .params(params(estimate, updated))
                    .param("version", estimate.version())
                    .update();
        }
        if (changed != 1) {
            throw new ConcurrencyConflictException(estimate.userId(), estimate.version());
        }
    }

    private static java.util.Map<String, Object> params(AbilityEstimate estimate, Instant updated) {
        return java.util.Map.of(
                "user", estimate.userId(),
                "section", estimate.section().name(),
                "theta", round(estimate.theta()),
                "info", estimate.info(),
                "n", estimate.n(),
                "at", Timestamp.from(updated));
    }

    @Override
    public boolean recordHistory(UUID attemptId, AbilityEstimate before, AbilityEstimate after) {
        return jdbc.sql("""
                        INSERT INTO ability_history (user_id, section, attempt_id, theta_before, theta_after,
                            standard_error, items_count)
                        VALUES (:user, :section, :attempt, :before, :after, :se, :items)
                        ON CONFLICT (attempt_id) DO NOTHING
                        """)
                        .param("user", after.userId())
                        .param("section", after.section().name())
                        .param("attempt", attemptId)
                        .param("before", round(before.theta()))
                        .param("after", round(after.theta()))
                        .param("se", round(after.standardError()))
                        .param("items", after.n() - before.n())
                        .update()
                == 1;
    }

    @Override
    public Optional<AbilityChange> change(UUID attemptId) {
        return jdbc.sql("""
                        SELECT section, theta_before, theta_after, standard_error FROM ability_history
                        WHERE attempt_id = :attempt
                        """)
                .param("attempt", attemptId)
                .query((rs, row) -> new AbilityChange(
                        Section.valueOf(rs.getString(1)), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4)))
                .optional();
    }

    @Override
    public void addResponses(UUID userId, Section section, List<Response> responses) {
        for (Response response : responses) {
            jdbc.sql("INSERT INTO ability_response (user_id, section, b, x) VALUES (:user, :section, :b, :x)")
                    .param("user", userId)
                    .param("section", section.name())
                    .param("b", round(response.b()))
                    .param("x", round(response.x()))
                    .update();
        }
    }

    @Override
    public List<Response> responses(UUID userId, Section section) {
        return jdbc.sql("SELECT b, x FROM ability_response WHERE user_id = :user AND section = :section ORDER BY id")
                .param("user", userId)
                .param("section", section.name())
                .query((rs, row) -> new Response(rs.getDouble(1), rs.getDouble(2)))
                .list();
    }

    @Override
    public int eraseUser(UUID userId) {
        int deleted = 0;
        for (String table : List.of("ability_response", "ability_history", "ability_estimate")) {
            deleted += jdbc.sql("DELETE FROM " + table + " WHERE user_id = :user")
                    .param("user", userId)
                    .update();
        }
        return deleted;
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    private static AbilityEstimate map(ResultSet rs, int row) throws SQLException {
        return new AbilityEstimate(
                rs.getObject("user_id", UUID.class),
                Section.valueOf(rs.getString("section")),
                rs.getDouble("theta"),
                rs.getDouble("info"),
                rs.getInt("n"),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getInt("version"));
    }
}
