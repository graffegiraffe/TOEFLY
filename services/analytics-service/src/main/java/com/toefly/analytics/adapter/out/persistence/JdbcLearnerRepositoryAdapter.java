package com.toefly.analytics.adapter.out.persistence;

import com.toefly.analytics.application.port.out.LearnerRepository;
import com.toefly.analytics.domain.common.Section;
import com.toefly.analytics.domain.learner.LearnerSettings;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcLearnerRepositoryAdapter implements LearnerRepository {

    private static final TypeReference<Map<Section, Double>> TARGETS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    JdbcLearnerRepositoryAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<LearnerSettings> find(UUID userId) {
        return jdbc.sql("SELECT * FROM learner WHERE user_id = :user")
                .param("user", userId)
                .query((rs, n) -> {
                    Timestamp scheduleAsOf = rs.getTimestamp("schedule_as_of");
                    Timestamp goalAsOf = rs.getTimestamp("goal_as_of");
                    Date exam = rs.getDate("exam_date");
                    Date planStart = rs.getDate("plan_start");
                    BigDecimal target = rs.getBigDecimal("overall_target");
                    return new LearnerSettings(
                            userId,
                            ZoneId.of(rs.getString("timezone")),
                            rs.getInt("minutes_per_day"),
                            scheduleAsOf == null ? null : scheduleAsOf.toInstant(),
                            exam == null ? null : exam.toLocalDate(),
                            target == null ? null : target.doubleValue(),
                            json.readValue(rs.getString("section_targets"), TARGETS),
                            goalAsOf == null ? null : goalAsOf.toInstant(),
                            planStart == null ? null : planStart.toLocalDate());
                })
                .optional();
    }

    @Override
    public void save(LearnerSettings s) {
        jdbc.sql("""
                        INSERT INTO learner (user_id, timezone, minutes_per_day, schedule_as_of, exam_date,
                                             overall_target, section_targets, goal_as_of, plan_start)
                        VALUES (:user, :zone, :minutes, :scheduleAsOf, :exam, :target, CAST(:targets AS jsonb), :goalAsOf,
                                :planStart)
                        ON CONFLICT (user_id) DO UPDATE SET
                            timezone = EXCLUDED.timezone, minutes_per_day = EXCLUDED.minutes_per_day,
                            schedule_as_of = EXCLUDED.schedule_as_of, exam_date = EXCLUDED.exam_date,
                            overall_target = EXCLUDED.overall_target, section_targets = EXCLUDED.section_targets,
                            goal_as_of = EXCLUDED.goal_as_of, plan_start = EXCLUDED.plan_start
                        """)
                .param("user", s.userId())
                .param("zone", s.timezone().getId())
                .param("minutes", s.minutesPerDay())
                .param("scheduleAsOf", s.scheduleAsOf() == null ? null : Timestamp.from(s.scheduleAsOf()))
                .param("exam", s.examDate() == null ? null : Date.valueOf(s.examDate()))
                .param("target", s.overallTarget())
                .param("targets", json.writeValueAsString(s.sectionTargets()))
                .param("goalAsOf", s.goalAsOf() == null ? null : Timestamp.from(s.goalAsOf()))
                .param("planStart", s.planStart() == null ? null : Date.valueOf(s.planStart()))
                .update();
    }

    @Override
    public void reset() {
        jdbc.sql("DELETE FROM learner").update();
    }

    @Override
    public void deleteUser(UUID userId) {
        jdbc.sql("DELETE FROM learner WHERE user_id = :user")
                .param("user", userId)
                .update();
    }
}
