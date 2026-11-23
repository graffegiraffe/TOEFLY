package com.toefly.tutoragent.adapter.out.persistence;

import com.toefly.tutoragent.application.port.out.ProjectionRepository;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Projections and erasure in tutor_agent_db. */
@Component
public class JdbcProjectionAdapter implements ProjectionRepository {

    /** An exam attempt that started earlier than this is treated as abandoned. */
    static final Duration EXAM_WINDOW = Duration.ofHours(4);

    private final JdbcClient jdbc;

    public JdbcProjectionAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void entitlements(UUID userId, Collection<String> entitlements, long version, Instant at) {
        jdbc.sql("""
                        INSERT INTO entitlement_view (user_id, features, version, updated_at) VALUES (:u, :f, :v, :at)
                        ON CONFLICT (user_id) DO UPDATE SET features = EXCLUDED.features, version = EXCLUDED.version,
                            updated_at = EXCLUDED.updated_at
                        WHERE entitlement_view.version < EXCLUDED.version
                        """)
                .param("u", userId)
                .param("f", entitlements.toArray(String[]::new))
                .param("v", version)
                .param("at", Timestamp.from(at))
                .update();
    }

    @Override
    public void examStarted(UUID userId, UUID attemptId, Instant at) {
        jdbc.sql("""
                        INSERT INTO active_exam_session (attempt_id, user_id, started_at) VALUES (:a, :u, :at)
                        ON CONFLICT DO NOTHING
                        """)
                .param("a", attemptId)
                .param("u", userId)
                .param("at", Timestamp.from(at))
                .update();
    }

    @Override
    public void examEnded(UUID attemptId) {
        jdbc.sql("DELETE FROM active_exam_session WHERE attempt_id = :a")
                .param("a", attemptId)
                .update();
    }

    @Override
    public boolean examInProgress(UUID userId, Instant now) {
        return jdbc.sql("SELECT count(*) FROM active_exam_session WHERE user_id = :u AND started_at > :since")
                        .param("u", userId)
                        .param("since", Timestamp.from(now.minus(EXAM_WINDOW)))
                        .query(Long.class)
                        .single()
                > 0;
    }

    @Override
    public void activity(UUID userId, Instant at) {
        jdbc.sql("""
                        INSERT INTO learner_activity (user_id, last_active_at) VALUES (:u, :at)
                        ON CONFLICT (user_id) DO UPDATE SET last_active_at = GREATEST(learner_activity.last_active_at,
                            EXCLUDED.last_active_at)
                        """).param("u", userId).param("at", Timestamp.from(at)).update();
    }

    @Override
    public void profile(UUID userId, @Nullable String timezone, @Nullable LocalDate examDate) {
        jdbc.sql("""
                        INSERT INTO learner_activity (user_id, last_active_at, timezone, exam_date) VALUES (:u, now(), :tz, :d)
                        ON CONFLICT (user_id) DO UPDATE SET timezone = COALESCE(EXCLUDED.timezone, learner_activity.timezone),
                            exam_date = COALESCE(EXCLUDED.exam_date, learner_activity.exam_date)
                        """)
                .param("u", userId)
                .param("tz", timezone)
                .param("d", examDate == null ? null : Date.valueOf(examDate))
                .update();
    }

    @Override
    public void weekProgress(
            UUID userId, UUID weekId, int weekNo, int progressPercent, boolean rebalanced, Instant at) {
        jdbc.sql("""
                        INSERT INTO week_progress_view (user_id, week_id, week_no, progress_pct, rebalanced_at, updated_at)
                        VALUES (:u, :w, :n, :p, :r, :at)
                        ON CONFLICT (user_id) DO UPDATE SET week_id = EXCLUDED.week_id, week_no = EXCLUDED.week_no,
                            progress_pct = EXCLUDED.progress_pct,
                            rebalanced_at = COALESCE(EXCLUDED.rebalanced_at, week_progress_view.rebalanced_at),
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("u", userId)
                .param("w", weekId)
                .param("n", weekNo)
                .param("p", progressPercent)
                .param("r", rebalanced ? Timestamp.from(at) : null)
                .param("at", Timestamp.from(at))
                .update();
    }

    @Override
    public void erase(UUID userId) {
        for (String table : List.of(
                "chat_thread",
                "pending_action",
                "mistake_memory",
                "mistake_record",
                "mistake_opportunity",
                "learner_model",
                "learner_model_snapshot",
                "learner_model_publication",
                "weekly_review",
                "deferred_review",
                "practice_result",
                "entitlement_view",
                "active_exam_session",
                "learner_activity",
                "week_progress_view",
                "reengagement_message")) {
            jdbc.sql("DELETE FROM " + table + " WHERE user_id = :u")
                    .param("u", userId)
                    .update();
        }
    }
}
