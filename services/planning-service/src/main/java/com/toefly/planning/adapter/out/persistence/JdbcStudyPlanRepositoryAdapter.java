package com.toefly.planning.adapter.out.persistence;

import com.toefly.planning.application.port.out.ConcurrencyConflictException;
import com.toefly.planning.application.port.out.StudyPlanRepository;
import com.toefly.planning.domain.plan.StudyPlan;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcStudyPlanRepositoryAdapter implements StudyPlanRepository {

    private static final TypeReference<Map<String, Double>> TARGETS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcStudyPlanRepositoryAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<StudyPlan> findByUser(UUID userId) {
        return jdbc.sql("SELECT * FROM study_plan WHERE user_id = :user")
                .param("user", userId)
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<StudyPlan> findById(UUID planId) {
        return jdbc.sql("SELECT * FROM study_plan WHERE id = :id")
                .param("id", planId)
                .query(this::map)
                .optional();
    }

    @Override
    public void insert(StudyPlan plan) {
        jdbc.sql("""
                        INSERT INTO study_plan (id, user_id, exam_date, weeks_total, start_date, timezone,
                            minutes_per_day, current_week_no, targets, goal_revision, created_at, version)
                        VALUES (:id, :user, :exam, :weeks, :start, :zone, :minutes, :current, CAST(:targets AS jsonb),
                            :revision, :created, :version)
                        """)
                .params(params(plan))
                .param("user", plan.userId())
                .param("start", Date.valueOf(plan.startDate()))
                .param("created", Timestamp.from(plan.createdAt()))
                .update();
    }

    @Override
    public int update(StudyPlan plan) {
        int changed = jdbc.sql("""
                        UPDATE study_plan SET exam_date = :exam, weeks_total = :weeks, timezone = :zone,
                            minutes_per_day = :minutes, current_week_no = :current, targets = CAST(:targets AS jsonb),
                            goal_revision = :revision, version = version + 1
                        WHERE id = :id AND version = :version
                        """).params(params(plan)).update();
        if (changed != 1) {
            throw new ConcurrencyConflictException("study plan " + plan.id());
        }
        plan.saved(plan.version() + 1);
        return plan.version();
    }

    @Override
    public void deleteByUser(UUID userId) {
        jdbc.sql("DELETE FROM study_plan WHERE user_id = :user")
                .param("user", userId)
                .update();
    }

    private Map<String, Object> params(StudyPlan plan) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", plan.id());
        params.put("exam", plan.examDate() == null ? null : Date.valueOf(plan.examDate()));
        params.put("weeks", plan.weeksTotal());
        params.put("zone", plan.timezone().getId());
        params.put("minutes", plan.minutesPerDay());
        params.put("current", plan.currentWeekNo());
        params.put("targets", json.writeValueAsString(plan.targets()));
        params.put("revision", plan.goalRevision());
        params.put("version", plan.version());
        return params;
    }

    private StudyPlan map(ResultSet rs, int row) throws SQLException {
        Date exam = rs.getDate("exam_date");
        return new StudyPlan(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                exam == null ? null : exam.toLocalDate(),
                rs.getInt("weeks_total"),
                rs.getDate("start_date").toLocalDate(),
                ZoneId.of(rs.getString("timezone")),
                rs.getInt("minutes_per_day"),
                rs.getInt("current_week_no"),
                json.readValue(rs.getString("targets"), TARGETS),
                rs.getInt("goal_revision"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getInt("version"));
    }
}
