package com.toefly.vocabulary.adapter.out.persistence;

import com.toefly.vocabulary.application.port.out.ReviewSessionRepository;
import com.toefly.vocabulary.domain.review.ReviewSession;
import com.toefly.vocabulary.domain.review.SessionStatus;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** review_session. */
@Repository
public class JdbcReviewSessionRepositoryAdapter implements ReviewSessionRepository {

    private static final String SELECT = """
            SELECT id, user_id, week_task_id, local_date, timezone, planned_card_ids::text[] AS planned,
                reviewed_card_ids::text[] AS reviewed, grades, again_count, new_introduced, active_seconds, status,
                started_at, completed_at, version
            FROM review_session
            """;

    private final JdbcClient jdbc;

    public JdbcReviewSessionRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReviewSession> find(UUID userId, UUID sessionId) {
        return jdbc.sql(SELECT + "WHERE user_id = :user AND id = :id")
                .param("user", userId)
                .param("id", sessionId)
                .query((rs, row) -> session(rs))
                .optional();
    }

    @Override
    public Optional<ReviewSession> findForUpdate(UUID userId, UUID sessionId) {
        return jdbc.sql(SELECT + "WHERE user_id = :user AND id = :id FOR UPDATE")
                .param("user", userId)
                .param("id", sessionId)
                .query((rs, row) -> session(rs))
                .optional();
    }

    @Override
    public void insert(ReviewSession s) {
        jdbc.sql("""
                        INSERT INTO review_session (id, user_id, week_task_id, local_date, timezone, planned_card_ids,
                            reviewed_card_ids, grades, again_count, new_introduced, active_seconds, status, started_at,
                            completed_at, version)
                        VALUES (:id, :user, :weekTask, :date, :zone, :planned::uuid[], :reviewed::uuid[], :grades,
                            :again, :fresh, :seconds, :status, :started, :completed, 0)
                        """).paramSource(params(s)).update();
    }

    @Override
    public ReviewSession update(ReviewSession s) {
        int updated = jdbc.sql("""
                        UPDATE review_session SET reviewed_card_ids = :reviewed::uuid[], grades = :grades,
                            again_count = :again, new_introduced = :fresh, active_seconds = :seconds,
                            status = :status, completed_at = :completed, version = version + 1
                        WHERE id = :id AND user_id = :user AND version = :version
                        """)
                .paramSource(params(s).addValue("version", s.version()))
                .update();
        if (updated != 1) {
            throw new OptimisticLockingFailureException("review_session " + s.id() + " changed concurrently");
        }
        return new ReviewSession(
                s.id(),
                s.userId(),
                s.weekTaskId(),
                s.localDate(),
                s.timezone(),
                s.plannedCardIds(),
                s.reviewedCardIds(),
                s.grades(),
                s.againCount(),
                s.newIntroduced(),
                s.status(),
                s.startedAt(),
                s.completedAt(),
                s.activeSeconds(),
                s.version() + 1);
    }

    @Override
    public void deleteAllOf(UUID userId) {
        jdbc.sql("DELETE FROM review_session WHERE user_id = :user")
                .param("user", userId)
                .update();
    }

    private static MapSqlParameterSource params(ReviewSession s) {
        return new MapSqlParameterSource()
                .addValue("id", s.id())
                .addValue("user", s.userId())
                .addValue("weekTask", s.weekTaskId())
                .addValue("date", s.localDate())
                .addValue("zone", s.timezone())
                .addValue("planned", array(s.plannedCardIds()))
                .addValue("reviewed", array(s.reviewedCardIds()))
                .addValue("grades", s.grades())
                .addValue("again", s.againCount())
                .addValue("fresh", s.newIntroduced())
                .addValue("seconds", s.activeSeconds())
                .addValue("status", s.status().name())
                .addValue("started", Sql.ts(s.startedAt()))
                .addValue("completed", Sql.ts(s.completedAt()));
    }

    private static String array(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
    }

    private static ReviewSession session(ResultSet rs) throws SQLException {
        return new ReviewSession(
                Sql.requiredUuid(rs, "id"),
                Sql.requiredUuid(rs, "user_id"),
                Sql.uuid(rs, "week_task_id"),
                rs.getObject("local_date", java.time.LocalDate.class),
                rs.getString("timezone"),
                ids(rs.getArray("planned")),
                new LinkedHashSet<>(ids(rs.getArray("reviewed"))),
                rs.getInt("grades"),
                rs.getInt("again_count"),
                rs.getInt("new_introduced"),
                SessionStatus.valueOf(rs.getString("status")),
                Sql.requiredInstant(rs, "started_at"),
                Sql.instant(rs, "completed_at"),
                rs.getInt("active_seconds"),
                rs.getInt("version"));
    }

    private static List<UUID> ids(Array array) throws SQLException {
        List<UUID> ids = new ArrayList<>();
        if (array != null) {
            for (Object value : (Object[]) array.getArray()) {
                ids.add(UUID.fromString(value.toString()));
            }
        }
        return ids;
    }
}
