package com.toefly.planning.adapter.out.persistence;

import com.toefly.planning.application.port.out.LearnerSignalPort;
import com.toefly.planning.domain.composition.WeakTypes.Accuracy;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.WeekSection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLearnerSignalAdapter implements LearnerSignalPort {

    private final JdbcClient jdbc;

    public JdbcLearnerSignalAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void recordResult(UUID userId, UUID attemptId, TaskType type, int correct, int total, Instant scoredAt) {
        jdbc.sql("""
                        INSERT INTO attempt_result (attempt_id, user_id, task_type, correct_count, total_count, scored_at)
                        VALUES (:attempt, :user, :type, :correct, :total, :at)
                        ON CONFLICT (attempt_id) DO NOTHING
                        """)
                .param("attempt", attemptId)
                .param("user", userId)
                .param("type", type.name())
                .param("correct", correct)
                .param("total", total)
                .param("at", Timestamp.from(scoredAt))
                .update();
    }

    @Override
    public Map<TaskType, Accuracy> accuracySince(UUID userId, Instant since) {
        Map<TaskType, Accuracy> accuracy = new EnumMap<>(TaskType.class);
        jdbc.sql("""
                        SELECT task_type, sum(correct_count) AS correct, sum(total_count) AS total FROM attempt_result
                        WHERE user_id = :user AND scored_at >= :since GROUP BY task_type
                        """)
                .param("user", userId)
                .param("since", Timestamp.from(since))
                .query((rs, row) -> accuracy.put(
                        TaskType.valueOf(rs.getString("task_type")),
                        new Accuracy(rs.getInt("correct"), rs.getInt("total"))))
                .list();
        return accuracy;
    }

    @Override
    public void recordTheta(UUID userId, WeekSection section, double theta, Instant occurredAt) {
        jdbc.sql("""
                        INSERT INTO learner_theta (user_id, section, theta, occurred_at)
                        VALUES (:user, :section, :theta, :at)
                        ON CONFLICT (user_id, section) DO UPDATE SET theta = EXCLUDED.theta,
                            occurred_at = EXCLUDED.occurred_at
                        WHERE learner_theta.occurred_at <= EXCLUDED.occurred_at
                        """)
                .param("user", userId)
                .param("section", section.name())
                .param("theta", theta)
                .param("at", Timestamp.from(occurredAt))
                .update();
    }

    @Override
    public Map<WeekSection, Double> theta(UUID userId) {
        Map<WeekSection, Double> theta = new EnumMap<>(WeekSection.class);
        jdbc.sql("SELECT section, theta FROM learner_theta WHERE user_id = :user")
                .param("user", userId)
                .query((rs, row) -> theta.put(WeekSection.valueOf(rs.getString("section")), rs.getDouble("theta")))
                .list();
        return theta;
    }

    @Override
    public void deleteByUser(UUID userId) {
        jdbc.sql("DELETE FROM attempt_result WHERE user_id = :user")
                .param("user", userId)
                .update();
        jdbc.sql("DELETE FROM learner_theta WHERE user_id = :user")
                .param("user", userId)
                .update();
    }
}
