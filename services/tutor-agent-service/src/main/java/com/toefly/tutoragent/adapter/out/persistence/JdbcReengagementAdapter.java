package com.toefly.tutoragent.adapter.out.persistence;

import com.toefly.tutoragent.application.port.out.ReengagementRepository;
import com.toefly.tutoragent.domain.reengagement.ComeBackMessage;
import com.toefly.tutoragent.domain.reengagement.Situation;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Candidates from learner_activity and week_progress_view, written messages in reengagement_message. */
@Component
public class JdbcReengagementAdapter implements ReengagementRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcReengagementAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<Candidate> candidates(Instant idleBefore, Instant rebalancedAfter, int limit) {
        return jdbc.sql("""
                        SELECT a.user_id, a.last_active_at, a.timezone, a.exam_date,
                               COALESCE(w.week_no, 0) AS week_no, COALESCE(w.progress_pct, 0) AS progress_pct, w.rebalanced_at
                        FROM learner_activity a LEFT JOIN week_progress_view w ON w.user_id = a.user_id
                        WHERE NOT a.deleted AND (a.last_active_at < :idle OR w.rebalanced_at > :rebalanced)
                        ORDER BY a.user_id LIMIT :l
                        """)
                .param("idle", Timestamp.from(idleBefore))
                .param("rebalanced", Timestamp.from(rebalancedAfter))
                .param("l", limit)
                .query((rs, n) -> {
                    Date exam = rs.getDate("exam_date");
                    Timestamp rebalanced = rs.getTimestamp("rebalanced_at");
                    return new Candidate(
                            rs.getObject("user_id", UUID.class),
                            rs.getTimestamp("last_active_at").toInstant(),
                            rs.getString("timezone"),
                            exam == null ? null : exam.toLocalDate(),
                            rs.getInt("week_no"),
                            rs.getInt("progress_pct"),
                            rebalanced == null ? null : rebalanced.toInstant());
                })
                .list();
    }

    @Override
    public boolean written(UUID userId, Situation situation, String episodeKey) {
        return jdbc.sql(
                                "SELECT count(*) FROM reengagement_message WHERE user_id = :u AND situation = :s AND episode_key = :k")
                        .param("u", userId)
                        .param("s", situation.name())
                        .param("k", episodeKey)
                        .query(Integer.class)
                        .single()
                > 0;
    }

    @Override
    public void save(
            UUID id,
            UUID userId,
            Situation situation,
            String episodeKey,
            ComeBackMessage message,
            boolean aiFallback,
            Instant at) {
        jdbc.sql("""
                        INSERT INTO reengagement_message (id, user_id, situation, episode_key, payload, created_at)
                        VALUES (:id, :u, :s, :k, :p::jsonb, :at) ON CONFLICT DO NOTHING
                        """)
                .param("id", id)
                .param("u", userId)
                .param("s", situation.name())
                .param("k", episodeKey)
                .param("p", json.writeValueAsString(Map.of("message", message, "aiFallback", aiFallback)))
                .param("at", Timestamp.from(at))
                .update();
    }
}
