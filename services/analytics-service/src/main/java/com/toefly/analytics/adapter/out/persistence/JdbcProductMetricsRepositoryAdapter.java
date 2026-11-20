package com.toefly.analytics.adapter.out.persistence;

import com.toefly.analytics.application.port.out.ProductMetricsRepository;
import com.toefly.analytics.domain.metrics.LearnerMilestones;
import com.toefly.analytics.domain.metrics.ProductCounters;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProductMetricsRepositoryAdapter implements ProductMetricsRepository {

    private final JdbcClient jdbc;

    JdbcProductMetricsRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LearnerMilestones> milestones(UUID userId) {
        return jdbc.sql("SELECT * FROM learner_milestone WHERE user_id = :user")
                .param("user", userId)
                .query(JdbcProductMetricsRepositoryAdapter::milestones)
                .optional();
    }

    @Override
    public void saveMilestones(LearnerMilestones m) {
        jdbc.sql("""
                        INSERT INTO learner_milestone (user_id, registered_on, onboarded_on, plan_created_on, first_task_on)
                        VALUES (:user, :registered, :onboarded, :plan, :task)
                        ON CONFLICT (user_id) DO UPDATE SET
                            registered_on = EXCLUDED.registered_on, onboarded_on = EXCLUDED.onboarded_on,
                            plan_created_on = EXCLUDED.plan_created_on, first_task_on = EXCLUDED.first_task_on
                        """)
                .param("user", m.userId())
                .param("registered", date(m.registeredOn()))
                .param("onboarded", date(m.onboardedOn()))
                .param("plan", date(m.planCreatedOn()))
                .param("task", date(m.firstTaskOn()))
                .update();
    }

    @Override
    public List<LearnerMilestones> allMilestones() {
        return jdbc.sql("SELECT * FROM learner_milestone")
                .query(JdbcProductMetricsRepositoryAdapter::milestones)
                .list();
    }

    @Override
    public void add(LocalDate day, ProductCounters.Counter counter, int delta) {
        String column = switch (counter) {
            case NEW_USERS -> "new_users";
            case ONBOARDED -> "onboarded";
            case PLANS_CREATED -> "plans_created";
            case FIRST_TASKS -> "first_tasks";
            case ATTEMPTS_SUBMITTED -> "attempts_submitted";
            case WEEKS_COMPLETED -> "weeks_completed";
            case WEEKS_COMPLETED_BY_PROGRESS -> "weeks_completed_by_progress";
        };
        jdbc.sql("INSERT INTO product_metrics_daily (day, " + column + ") VALUES (:day, :delta)"
                        + " ON CONFLICT (day) DO UPDATE SET " + column + " = product_metrics_daily." + column
                        + " + EXCLUDED." + column)
                .param("day", Date.valueOf(day))
                .param("delta", delta)
                .update();
    }

    @Override
    public List<ProductCounters> counters(LocalDate from, LocalDate to) {
        return jdbc.sql("SELECT * FROM product_metrics_daily WHERE day >= :from AND day < :to ORDER BY day")
                .param("from", Date.valueOf(from))
                .param("to", Date.valueOf(to))
                .query((rs, n) -> new ProductCounters(
                        rs.getDate("day").toLocalDate(),
                        rs.getInt("new_users"),
                        rs.getInt("onboarded"),
                        rs.getInt("plans_created"),
                        rs.getInt("first_tasks"),
                        rs.getInt("attempts_submitted"),
                        rs.getInt("weeks_completed"),
                        rs.getInt("weeks_completed_by_progress")))
                .list();
    }

    @Override
    public void reset() {
        jdbc.sql("DELETE FROM product_metrics_daily").update();
        jdbc.sql("DELETE FROM learner_milestone").update();
    }

    @Override
    public void deleteUser(UUID userId) {
        jdbc.sql("DELETE FROM learner_milestone WHERE user_id = :user")
                .param("user", userId)
                .update();
    }

    private static LearnerMilestones milestones(ResultSet rs, int n) throws SQLException {
        return new LearnerMilestones(
                rs.getObject("user_id", UUID.class),
                local(rs.getDate("registered_on")),
                local(rs.getDate("onboarded_on")),
                local(rs.getDate("plan_created_on")),
                local(rs.getDate("first_task_on")));
    }

    private static @Nullable LocalDate local(@Nullable Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private static @Nullable Date date(@Nullable LocalDate day) {
        return day == null ? null : Date.valueOf(day);
    }
}
