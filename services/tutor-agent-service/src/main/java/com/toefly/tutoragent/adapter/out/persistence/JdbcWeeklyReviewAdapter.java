package com.toefly.tutoragent.adapter.out.persistence;

import com.toefly.tutoragent.application.port.out.WeeklyReviewRepository;
import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import com.toefly.tutoragent.domain.review.WeekFacts;
import com.toefly.tutoragent.domain.review.WeeklyReview;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Weekly reviews; the text and the facts are one JSON document, a regeneration overwrites the week's row. */
@Component
public class JdbcWeeklyReviewAdapter implements WeeklyReviewRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcWeeklyReviewAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    record Payload(WeeklyReview.Text text, WeekFacts facts) {}

    @Override
    public void save(WeeklyReview review) {
        jdbc.sql("""
                        INSERT INTO weekly_review (id, user_id, week_id, week_no, language, ai_fallback, low_activity,
                            payload, tutor_note, generated_at)
                        VALUES (:id, :u, :w, :n, :l, :f, :low, :p::jsonb, :note, :at)
                        ON CONFLICT (week_id) DO UPDATE SET language = EXCLUDED.language,
                            ai_fallback = EXCLUDED.ai_fallback, low_activity = EXCLUDED.low_activity,
                            payload = EXCLUDED.payload, generated_at = EXCLUDED.generated_at
                        """)
                .param("id", review.id())
                .param("u", review.userId())
                .param("w", review.weekId())
                .param("n", review.weekNo())
                .param("l", review.language().name())
                .param("f", review.aiFallback())
                .param("low", review.lowActivity())
                .param("p", json.writeValueAsString(new Payload(review.text(), review.facts())))
                .param("note", review.tutorNote())
                .param("at", Timestamp.from(review.generatedAt()))
                .update();
    }

    @Override
    public Optional<WeeklyReview> find(UUID reviewId) {
        return jdbc.sql("SELECT * FROM weekly_review WHERE id = :id")
                .param("id", reviewId)
                .query((rs, n) -> review(rs))
                .optional();
    }

    @Override
    public Optional<WeeklyReview> findByWeek(UUID weekId) {
        return jdbc.sql("SELECT * FROM weekly_review WHERE week_id = :w")
                .param("w", weekId)
                .query((rs, n) -> review(rs))
                .optional();
    }

    @Override
    public List<WeeklyReview> list(UUID userId, @Nullable Integer beforeWeekNo, int limit) {
        return jdbc.sql("""
                        SELECT * FROM weekly_review WHERE user_id = :u AND week_no < :before
                        ORDER BY week_no DESC LIMIT :l
                        """)
                .param("u", userId)
                .param("before", beforeWeekNo == null ? Integer.MAX_VALUE : beforeWeekNo)
                .param("l", limit)
                .query((rs, n) -> review(rs))
                .list();
    }

    @Override
    public Optional<WeeklyReview> latest(UUID userId, int beforeWeekNo) {
        return list(userId, beforeWeekNo, 1).stream().findFirst();
    }

    private WeeklyReview review(ResultSet rs) throws SQLException {
        Payload payload = json.readValue(rs.getString("payload"), Payload.class);
        return new WeeklyReview(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("week_id", UUID.class),
                rs.getInt("week_no"),
                rs.getTimestamp("generated_at").toInstant(),
                ExplanationLanguage.of(rs.getString("language")),
                rs.getBoolean("ai_fallback"),
                rs.getBoolean("low_activity"),
                payload.text(),
                payload.facts(),
                rs.getString("tutor_note"));
    }
}
