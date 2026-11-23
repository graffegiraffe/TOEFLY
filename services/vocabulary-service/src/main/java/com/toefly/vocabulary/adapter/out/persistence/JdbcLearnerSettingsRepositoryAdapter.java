package com.toefly.vocabulary.adapter.out.persistence;

import com.toefly.vocabulary.application.port.out.LearnerSettingsRepository;
import com.toefly.vocabulary.domain.review.DailyLimits;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** learner_settings. */
@Repository
public class JdbcLearnerSettingsRepositoryAdapter implements LearnerSettingsRepository {

    private final JdbcClient jdbc;

    public JdbcLearnerSettingsRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LearnerSettings> find(UUID userId) {
        return jdbc.sql("SELECT timezone, new_cards_per_day FROM learner_settings WHERE user_id = :user")
                .param("user", userId)
                .query((rs, row) -> new LearnerSettings(zone(rs.getString(1)), DailyLimits.of(rs.getInt(2))))
                .optional();
    }

    @Override
    public void saveTimezone(UUID userId, ZoneId zone) {
        jdbc.sql("""
                        INSERT INTO learner_settings (user_id, timezone, updated_at) VALUES (:user, :zone, :at)
                        ON CONFLICT (user_id) DO UPDATE SET timezone = EXCLUDED.timezone, updated_at = EXCLUDED.updated_at
                        """)
                .param("user", userId)
                .param("zone", zone.getId())
                .param("at", Sql.ts(Instant.now()))
                .update();
    }

    @Override
    public void saveNewCardsPerDay(UUID userId, int newCardsPerDay) {
        jdbc.sql("""
                        INSERT INTO learner_settings (user_id, new_cards_per_day, updated_at) VALUES (:user, :n, :at)
                        ON CONFLICT (user_id) DO UPDATE SET new_cards_per_day = EXCLUDED.new_cards_per_day,
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("user", userId)
                .param("n", newCardsPerDay)
                .param("at", Sql.ts(Instant.now()))
                .update();
    }

    @Override
    public void delete(UUID userId) {
        jdbc.sql("DELETE FROM learner_settings WHERE user_id = :user")
                .param("user", userId)
                .update();
    }

    private static ZoneId zone(String id) {
        try {
            return ZoneId.of(id);
        } catch (DateTimeException unknown) {
            return ZoneOffset.UTC;
        }
    }
}
