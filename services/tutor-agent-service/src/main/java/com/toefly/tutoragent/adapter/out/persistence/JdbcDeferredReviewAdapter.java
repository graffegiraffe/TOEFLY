package com.toefly.tutoragent.adapter.out.persistence;

import com.toefly.tutoragent.application.port.out.ClosedWeek;
import com.toefly.tutoragent.application.port.out.DeferredReviewRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Reviews waiting for AI. */
@Component
public class JdbcDeferredReviewAdapter implements DeferredReviewRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcDeferredReviewAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void defer(ClosedWeek week, int attempts, Instant nextAttemptAt, String error) {
        jdbc.sql("""
                        INSERT INTO deferred_review (week_id, user_id, request, attempts, next_attempt_at, last_error, created_at)
                        VALUES (:w, :u, :r::jsonb, :a, :next, :e, now())
                        ON CONFLICT (week_id) DO UPDATE SET attempts = EXCLUDED.attempts,
                            next_attempt_at = EXCLUDED.next_attempt_at, last_error = EXCLUDED.last_error
                        """)
                .param("w", week.weekId())
                .param("u", week.userId())
                .param("r", json.writeValueAsString(week))
                .param("a", attempts)
                .param("next", Timestamp.from(nextAttemptAt))
                .param("e", error)
                .update();
    }

    @Override
    public List<Deferred> due(Instant now, int limit) {
        return jdbc.sql("""
                        SELECT request, attempts FROM deferred_review WHERE next_attempt_at <= :now
                        ORDER BY next_attempt_at LIMIT :l
                        """)
                .param("now", Timestamp.from(now))
                .param("l", limit)
                .query((rs, n) ->
                        new Deferred(json.readValue(rs.getString("request"), ClosedWeek.class), rs.getInt("attempts")))
                .list();
    }

    @Override
    public void remove(UUID weekId) {
        jdbc.sql("DELETE FROM deferred_review WHERE week_id = :w")
                .param("w", weekId)
                .update();
    }
}
