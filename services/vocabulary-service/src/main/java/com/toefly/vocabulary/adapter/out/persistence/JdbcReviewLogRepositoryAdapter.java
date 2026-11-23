package com.toefly.vocabulary.adapter.out.persistence;

import com.toefly.vocabulary.application.port.out.ReviewLogRepository;
import com.toefly.vocabulary.domain.card.ReviewLog;
import com.toefly.vocabulary.domain.fsrs.CardState;
import com.toefly.vocabulary.domain.fsrs.Rating;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** review_log, append-only; the unique index makes a repeated grade a no-op (INV-VOC-04). */
@Repository
public class JdbcReviewLogRepositoryAdapter implements ReviewLogRepository {

    private final JdbcClient jdbc;

    public JdbcReviewLogRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean append(ReviewLog log) {
        return jdbc.sql("""
                        INSERT INTO review_log (id, card_id, user_id, rating, state_before, state_after,
                            stability_before, difficulty_before, stability_after, difficulty_after, elapsed_days,
                            scheduled_days, session_id, sequence_no, reviewed_at, duration_ms)
                        VALUES (:id, :card, :user, :rating, :stateBefore, :stateAfter, :sBefore, :dBefore, :sAfter,
                            :dAfter, :elapsed, :scheduled, :session, :seq, :at, :duration)
                        ON CONFLICT (card_id, session_id, sequence_no) WHERE session_id IS NOT NULL DO NOTHING
                        """)
                        .param("id", log.id())
                        .param("card", log.cardId())
                        .param("user", log.userId())
                        .param("rating", log.rating().grade())
                        .param("stateBefore", log.stateBefore().name())
                        .param("stateAfter", log.stateAfter().name())
                        .param("sBefore", Sql.decimal(log.stabilityBefore()))
                        .param("dBefore", Sql.decimal(log.difficultyBefore()))
                        .param("sAfter", Sql.decimal(log.stabilityAfter()))
                        .param("dAfter", Sql.decimal(log.difficultyAfter()))
                        .param("elapsed", Sql.decimal(log.elapsedDays()))
                        .param("scheduled", Sql.decimal(log.scheduledDays()))
                        .param("session", log.sessionId())
                        .param("seq", log.sequenceNo())
                        .param("at", Sql.ts(log.reviewedAt()))
                        .param("duration", log.durationMs())
                        .update()
                == 1;
    }

    @Override
    public DayCounts countBetween(UUID userId, Instant from, Instant to) {
        return jdbc.sql("""
                        SELECT count(DISTINCT card_id) FILTER (WHERE state_before = 'NEW') AS fresh,
                               count(DISTINCT card_id) AS cards
                        FROM review_log WHERE user_id = :user AND reviewed_at >= :from AND reviewed_at < :to
                        """)
                .param("user", userId)
                .param("from", Sql.ts(from))
                .param("to", Sql.ts(to))
                .query((rs, row) -> new DayCounts(rs.getInt("fresh"), rs.getInt("cards")))
                .single();
    }

    @Override
    public List<ReviewLog> all(UUID userId) {
        return jdbc.sql("SELECT * FROM review_log WHERE user_id = :user ORDER BY reviewed_at, id")
                .param("user", userId)
                .query((rs, row) -> new ReviewLog(
                        Sql.requiredUuid(rs, "id"),
                        Sql.requiredUuid(rs, "card_id"),
                        Sql.requiredUuid(rs, "user_id"),
                        Rating.ofGrade(rs.getInt("rating")),
                        CardState.valueOf(rs.getString("state_before")),
                        CardState.valueOf(rs.getString("state_after")),
                        Sql.number(rs, "stability_before"),
                        Sql.number(rs, "difficulty_before"),
                        Sql.number(rs, "stability_after"),
                        Sql.number(rs, "difficulty_after"),
                        Sql.number(rs, "elapsed_days"),
                        Sql.number(rs, "scheduled_days"),
                        Sql.uuid(rs, "session_id"),
                        rs.getInt("sequence_no"),
                        Sql.requiredInstant(rs, "reviewed_at"),
                        rs.getInt("duration_ms")))
                .list();
    }
}
