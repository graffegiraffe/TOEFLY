package com.toefly.practice.adapter.out.persistence;

import com.toefly.practice.application.port.in.AttemptCursor;
import com.toefly.practice.application.port.in.AttemptQuery;
import com.toefly.practice.application.port.in.AttemptView;
import com.toefly.practice.application.port.out.AttemptViewPort;
import com.toefly.practice.domain.attempt.Answer;
import com.toefly.practice.domain.attempt.AttemptMode;
import com.toefly.practice.domain.attempt.AttemptStatus;
import com.toefly.practice.domain.attempt.Section;
import com.toefly.practice.domain.attempt.TaskType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;

/** attempt_view: inline projection of the attempt streams (docs/04-architecture.md §6.6). */
@Repository
public class JdbcAttemptViewAdapter implements AttemptViewPort {

    private static final TypeReference<Map<UUID, Answer>> ANSWERS = new TypeReference<>() {};
    private static final String COLUMNS = """
            attempt_id, user_id, item_set_id, item_set_version, task_type, section, mode, status, week_task_id,
            exam_session_id, started_at, submitted_at, scored_at, raw_score, max_score, item_count, correct_count,
            unanswered_count, active_seconds, estimated_seconds, answers::text AS answers, last_activity_at, version
            """;

    private final JdbcClient jdbc;

    public JdbcAttemptViewAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(AttemptView view) {
        upsert("attempt_view", view);
    }

    private void upsert(String table, AttemptView view) {
        jdbc.sql("""
                        INSERT INTO %s (attempt_id, user_id, item_set_id, item_set_version, task_type, section, mode,
                            status, week_task_id, exam_session_id, started_at, submitted_at, scored_at, raw_score,
                            max_score, item_count, correct_count, unanswered_count, active_seconds, estimated_seconds,
                            answers, last_activity_at, version)
                        VALUES (:id, :user, :set, :setVersion, :taskType, :section, :mode, :status, :weekTask,
                            :examSession, :startedAt, :submittedAt, :scoredAt, :rawScore, :maxScore, :itemCount,
                            :correct, :unanswered, :active, :estimated, :answers::jsonb, :lastActivity, :version)
                        ON CONFLICT (attempt_id) DO UPDATE SET status = EXCLUDED.status,
                            submitted_at = EXCLUDED.submitted_at, scored_at = EXCLUDED.scored_at,
                            raw_score = EXCLUDED.raw_score, correct_count = EXCLUDED.correct_count,
                            unanswered_count = EXCLUDED.unanswered_count, active_seconds = EXCLUDED.active_seconds,
                            answers = EXCLUDED.answers, last_activity_at = EXCLUDED.last_activity_at,
                            version = EXCLUDED.version
                        WHERE %s.version <= EXCLUDED.version
                        """.formatted(table, table))
                .param("id", view.attemptId())
                .param("user", view.userId())
                .param("set", view.itemSetId())
                .param("setVersion", view.itemSetVersion())
                .param("taskType", view.taskType().name())
                .param("section", view.section() == null ? null : view.section().name())
                .param("mode", view.mode().name())
                .param("status", view.status().name())
                .param("weekTask", view.weekTaskId())
                .param("examSession", view.examSessionId())
                .param("startedAt", Timestamp.from(view.startedAt()))
                .param("submittedAt", timestamp(view.submittedAt()))
                .param("scoredAt", timestamp(view.scoredAt()))
                .param("rawScore", view.rawScore())
                .param("maxScore", view.maxScore())
                .param("itemCount", view.itemCount())
                .param("correct", view.correctCount())
                .param("unanswered", view.unansweredCount())
                .param("active", view.activeSeconds())
                .param("estimated", view.estimatedSeconds())
                .param("answers", EventJson.MAPPER.writerFor(ANSWERS).writeValueAsString(view.answers()))
                .param("lastActivity", Timestamp.from(view.lastActivityAt()))
                .param("version", view.version())
                .update();
    }

    @Override
    public Optional<AttemptView> find(UUID attemptId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM attempt_view WHERE attempt_id = :id")
                .param("id", attemptId)
                .query(JdbcAttemptViewAdapter::map)
                .optional();
    }

    @Override
    public Optional<AttemptView> findInProgress(UUID userId, UUID itemSetId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM attempt_view
                        WHERE user_id = :user AND item_set_id = :set AND status = 'IN_PROGRESS' AND mode = 'PRACTICE'
                        """)
                .param("user", userId)
                .param("set", itemSetId)
                .query(JdbcAttemptViewAdapter::map)
                .optional();
    }

    @Override
    public boolean hasScored(UUID userId, UUID itemSetId) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM attempt_view
                        WHERE user_id = :user AND item_set_id = :set AND status = 'SCORED')
                        """)
                .param("user", userId)
                .param("set", itemSetId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public void touch(UUID attemptId, Instant at, int activeSeconds) {
        jdbc.sql("""
                        UPDATE attempt_view SET last_activity_at = :at, active_seconds = :active
                        WHERE attempt_id = :id AND status = 'IN_PROGRESS'
                        """)
                .param("id", attemptId)
                .param("at", Timestamp.from(at))
                .param("active", activeSeconds)
                .update();
    }

    @Override
    public List<AttemptView> page(AttemptQuery query, @Nullable AttemptCursor after, int limit) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM attempt_view WHERE user_id = :user");
        Map<String, Object> params = new HashMap<>();
        params.put("user", query.userId());
        if (query.itemSetId() != null) {
            sql.append(" AND item_set_id = :set");
            params.put("set", query.itemSetId());
        }
        if (query.weekTaskId() != null) {
            sql.append(" AND week_task_id = :weekTask");
            params.put("weekTask", query.weekTaskId());
        }
        if (query.mode() != null) {
            sql.append(" AND mode = :mode");
            params.put("mode", query.mode().name());
        }
        if (!query.statuses().isEmpty()) {
            sql.append(" AND status IN (:statuses)");
            params.put("statuses", query.statuses().stream().map(Enum::name).collect(Collectors.toList()));
        }
        if (query.section() != null) {
            sql.append(" AND section = :section");
            params.put("section", query.section().name());
        }
        if (query.from() != null) {
            sql.append(" AND started_at >= :from");
            params.put("from", Timestamp.from(query.from()));
        }
        if (query.to() != null) {
            sql.append(" AND started_at < :to");
            params.put("to", Timestamp.from(query.to()));
        }
        String direction = query.ascending() ? "ASC" : "DESC";
        if (after != null) {
            sql.append(
                    query.ascending()
                            ? " AND (started_at, attempt_id) > (:afterAt, :afterId)"
                            : " AND (started_at, attempt_id) < (:afterAt, :afterId)");
            params.put("afterAt", Timestamp.from(after.startedAt()));
            params.put("afterId", after.attemptId());
        }
        sql.append(" ORDER BY started_at ")
                .append(direction)
                .append(", attempt_id ")
                .append(direction);
        sql.append(" LIMIT :limit");
        params.put("limit", limit);
        return jdbc.sql(sql.toString())
                .params(params)
                .query(JdbcAttemptViewAdapter::map)
                .list();
    }

    @Override
    public List<UUID> stale(AttemptMode mode, Instant before, int limit) {
        return jdbc.sql("""
                        SELECT attempt_id FROM attempt_view
                        WHERE status = 'IN_PROGRESS' AND mode = :mode AND last_activity_at < :before
                        ORDER BY last_activity_at LIMIT :limit
                        """)
                .param("mode", mode.name())
                .param("before", Timestamp.from(before))
                .param("limit", limit)
                .query(UUID.class)
                .list();
    }

    /** Builds the new projection in a side table and swaps it in by renaming (docs/04-architecture.md §6.6). */
    @Override
    public void replaceAll(List<AttemptView> views) {
        jdbc.sql("DROP TABLE IF EXISTS attempt_view_rebuild").update();
        jdbc.sql("CREATE TABLE attempt_view_rebuild (LIKE attempt_view INCLUDING ALL)")
                .update();
        views.forEach(view -> upsert("attempt_view_rebuild", view));
        jdbc.sql("LOCK TABLE attempt_view IN ACCESS EXCLUSIVE MODE").update();
        jdbc.sql("DROP TABLE attempt_view").update();
        jdbc.sql("ALTER TABLE attempt_view_rebuild RENAME TO attempt_view").update();
    }

    @Override
    public int eraseUser(UUID userId) {
        return jdbc.sql("DELETE FROM attempt_view WHERE user_id = :user")
                .param("user", userId)
                .update();
    }

    private static AttemptView map(ResultSet rs, int row) throws SQLException {
        String section = rs.getString("section");
        java.math.BigDecimal rawScore = rs.getBigDecimal("raw_score");
        return new AttemptView(
                rs.getObject("attempt_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("item_set_id", UUID.class),
                rs.getInt("item_set_version"),
                TaskType.valueOf(rs.getString("task_type")),
                section == null ? null : Section.valueOf(section),
                AttemptMode.valueOf(rs.getString("mode")),
                AttemptStatus.valueOf(rs.getString("status")),
                rs.getObject("week_task_id", UUID.class),
                rs.getObject("exam_session_id", UUID.class),
                rs.getTimestamp("started_at").toInstant(),
                instant(rs.getTimestamp("submitted_at")),
                instant(rs.getTimestamp("scored_at")),
                rawScore == null ? null : rawScore.doubleValue(),
                rs.getDouble("max_score"),
                rs.getInt("item_count"),
                rs.getInt("correct_count"),
                rs.getInt("unanswered_count"),
                rs.getInt("active_seconds"),
                rs.getInt("estimated_seconds"),
                EventJson.MAPPER.readValue(rs.getString("answers"), ANSWERS),
                rs.getTimestamp("last_activity_at").toInstant(),
                rs.getInt("version"));
    }

    private static @Nullable Timestamp timestamp(@Nullable Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static @Nullable Instant instant(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
