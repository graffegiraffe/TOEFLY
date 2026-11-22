package com.toefly.billing.adapter.out.persistence;

import com.toefly.billing.application.port.out.SagaRepository;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.saga.PaymentSaga;
import com.toefly.billing.domain.saga.PaymentSagaData;
import com.toefly.billing.domain.saga.PaymentSagaStep;
import com.toefly.billing.domain.saga.SagaStatus;
import com.toefly.billing.domain.saga.SagaStep;
import com.toefly.billing.domain.saga.StepStatus;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcSagaRepositoryAdapter implements SagaRepository {

    static final String COLUMNS = """
            id, correlation_id, status, current_step, user_id, plan_tier_code, provider, data::text AS data,
            next_run_at, created_at, updated_at
            """;

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcSagaRepositoryAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean insertIfAbsent(PaymentSaga saga) {
        int inserted = jdbc.sql("""
                        INSERT INTO saga_instance (id, saga_type, correlation_id, status, current_step, user_id,
                            plan_tier_code, provider, data, next_run_at, created_at, updated_at)
                        VALUES (:id, :type, :correlation, :status, :current, :user, :tier, :provider, :data::jsonb,
                            :next, :createdAt, :updatedAt)
                        ON CONFLICT (correlation_id) DO NOTHING
                        """)
                .param("id", saga.id())
                .param("type", PaymentSaga.TYPE)
                .param("correlation", saga.correlationId())
                .param("status", saga.status().name())
                .param("current", saga.currentStep())
                .param("user", saga.userId())
                .param("tier", saga.tier().name())
                .param("provider", saga.provider().name())
                .param("data", json.writeValueAsString(saga.data()))
                .param("next", Timestamp.from(saga.nextRunAt()))
                .param("createdAt", Timestamp.from(saga.createdAt()))
                .param("updatedAt", Timestamp.from(saga.updatedAt()))
                .update();
        if (inserted == 1) {
            writeSteps(saga);
        }
        return inserted == 1;
    }

    @Override
    public void update(PaymentSaga saga) {
        jdbc.sql("""
                        UPDATE saga_instance SET status = :status, current_step = :current, data = :data::jsonb,
                            next_run_at = :next, updated_at = :updatedAt
                        WHERE id = :id
                        """)
                .param("status", saga.status().name())
                .param("current", saga.currentStep())
                .param("data", json.writeValueAsString(saga.data()))
                .param("next", Timestamp.from(saga.nextRunAt()))
                .param("updatedAt", Timestamp.from(saga.updatedAt()))
                .param("id", saga.id())
                .update();
        writeSteps(saga);
    }

    private void writeSteps(PaymentSaga saga) {
        for (SagaStep step : saga.steps()) {
            jdbc.sql("""
                            INSERT INTO saga_step (saga_id, step_no, name, status, attempts, next_attempt_at,
                                last_error)
                            VALUES (:saga, :no, :name, :status, :attempts, :next, :error)
                            ON CONFLICT (saga_id, step_no) DO UPDATE SET status = EXCLUDED.status,
                                attempts = EXCLUDED.attempts, next_attempt_at = EXCLUDED.next_attempt_at,
                                last_error = EXCLUDED.last_error
                            """)
                    .param("saga", saga.id())
                    .param("no", step.step().no())
                    .param("name", step.step().name())
                    .param("status", step.status().name())
                    .param("attempts", step.attempts())
                    .param("next", Sql.ts(step.nextAttemptAt()))
                    .param("error", step.lastError())
                    .update();
        }
    }

    @Override
    public Optional<PaymentSaga> findById(UUID id) {
        return one("WHERE id = :id", id, "");
    }

    @Override
    public Optional<PaymentSaga> findByCorrelation(UUID correlationId) {
        return one("WHERE correlation_id = :id", correlationId, "");
    }

    @Override
    public Optional<PaymentSaga> lockDue(Instant now) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM saga_instance
                        WHERE status IN ('RUNNING', 'COMPENSATING') AND next_run_at <= :now
                        ORDER BY next_run_at LIMIT 1 FOR UPDATE SKIP LOCKED
                        """)
                .param("now", Timestamp.from(now))
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<PaymentSaga> lock(UUID id) {
        return one("WHERE id = :id", id, " FOR UPDATE SKIP LOCKED");
    }

    @Override
    public Page page(
            @Nullable SagaStatus status, @Nullable Instant beforeUpdatedAt, @Nullable UUID beforeId, int limit) {
        List<PaymentSaga> rows = jdbc.sql("SELECT " + COLUMNS + """
                         FROM saga_instance
                        WHERE (CAST(:status AS varchar) IS NULL OR status = :status)
                          AND (CAST(:before AS timestamptz) IS NULL OR (updated_at, id) < (:before, :beforeId))
                        ORDER BY updated_at DESC, id DESC LIMIT :limit
                        """)
                .param("status", Sql.name(status))
                .param("before", Sql.ts(beforeUpdatedAt))
                .param("beforeId", beforeId == null ? new UUID(0, 0) : beforeId)
                .param("limit", limit + 1)
                .query(this::map)
                .list();
        boolean more = rows.size() > limit;
        return new Page(more ? rows.subList(0, limit) : rows, more);
    }

    private Optional<PaymentSaga> one(String where, UUID id, String lock) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM saga_instance " + where + lock)
                .param("id", id)
                .query(this::map)
                .optional();
    }

    private PaymentSaga map(ResultSet rs, int row) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<SagaStep> steps = jdbc.sql("""
                        SELECT step_no, status, attempts, next_attempt_at, last_error FROM saga_step
                        WHERE saga_id = :id ORDER BY step_no
                        """)
                .param("id", id)
                .query((stepRs, n) -> new SagaStep(
                        PaymentSagaStep.of(stepRs.getInt("step_no")),
                        StepStatus.valueOf(stepRs.getString("status")),
                        stepRs.getInt("attempts"),
                        Sql.instant(stepRs, "next_attempt_at"),
                        stepRs.getString("last_error")))
                .list();
        return new PaymentSaga(
                id,
                rs.getObject("correlation_id", UUID.class),
                SagaStatus.valueOf(rs.getString("status")),
                rs.getInt("current_step"),
                rs.getObject("user_id", UUID.class),
                PlanTierCode.valueOf(rs.getString("plan_tier_code")),
                PaymentProviderCode.valueOf(rs.getString("provider")),
                json.readValue(rs.getString("data"), PaymentSagaData.class),
                Sql.requiredInstant(rs, "next_run_at"),
                steps,
                Sql.requiredInstant(rs, "created_at"),
                Sql.requiredInstant(rs, "updated_at"));
    }
}
