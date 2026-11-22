package com.toefly.billing.adapter.out.persistence;

import com.toefly.billing.application.port.out.SubscriptionRepository;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.EndReason;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import com.toefly.billing.domain.subscription.Subscription;
import com.toefly.billing.domain.subscription.SubscriptionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSubscriptionRepositoryAdapter implements SubscriptionRepository {

    static final String COLUMNS = """
            id, user_id, plan_tier_code, provider, provider_subscription_id, provider_customer_id, status,
            current_period_start, current_period_end, trial_ends_at, trial_ending_notified, cancel_at_period_end,
            canceled_at, grace_until, pending_plan_tier_code, test_mode, ended_at, end_reason, created_at, version
            """;

    private final JdbcClient jdbc;

    public JdbcSubscriptionRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Subscription> findLive(UUID userId) {
        return one("WHERE user_id = :id AND status <> 'EXPIRED'", userId, false);
    }

    @Override
    public Optional<Subscription> lockLive(UUID userId) {
        return one("WHERE user_id = :id AND status <> 'EXPIRED'", userId, true);
    }

    @Override
    public Optional<Subscription> findById(UUID id) {
        return one("WHERE id = :id", id, false);
    }

    @Override
    public Optional<Subscription> lockById(UUID id) {
        return one("WHERE id = :id", id, true);
    }

    @Override
    public Optional<Subscription> findByProviderRef(PaymentProviderCode provider, String providerSubscriptionId) {
        return jdbc.sql("SELECT " + COLUMNS
                        + " FROM subscription WHERE provider = :provider AND provider_subscription_id = :ref")
                .param("provider", provider.name())
                .param("ref", providerSubscriptionId)
                .query(JdbcSubscriptionRepositoryAdapter::map)
                .optional();
    }

    @Override
    public List<Subscription> findByUser(UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM subscription WHERE user_id = :id ORDER BY created_at DESC")
                .param("id", userId)
                .query(JdbcSubscriptionRepositoryAdapter::map)
                .list();
    }

    @Override
    public Subscription save(Subscription s) {
        int updated = jdbc.sql("""
                        UPDATE subscription SET plan_tier_code = :tier, provider = :provider,
                            provider_subscription_id = :ref, provider_customer_id = :customer, status = :status,
                            current_period_start = :start, current_period_end = :end, trial_ends_at = :trialEnds,
                            trial_ending_notified = :notified, cancel_at_period_end = :cancel,
                            canceled_at = :canceledAt, grace_until = :grace, pending_plan_tier_code = :pending,
                            test_mode = :test, ended_at = :endedAt, end_reason = :reason, version = version + 1
                        WHERE id = :id AND version = :version
                        """).paramSource(params(s)).update();
        if (updated == 0) {
            if (findById(s.id()).isPresent()) {
                throw new OptimisticLockingFailureException("subscription " + s.id() + " changed concurrently");
            }
            jdbc.sql("""
                            INSERT INTO subscription (id, user_id, plan_tier_code, provider, provider_subscription_id,
                                provider_customer_id, status, current_period_start, current_period_end, trial_ends_at,
                                trial_ending_notified, cancel_at_period_end, canceled_at, grace_until,
                                pending_plan_tier_code, test_mode, ended_at, end_reason, created_at, version)
                            VALUES (:id, :user, :tier, :provider, :ref, :customer, :status, :start, :end, :trialEnds,
                                :notified, :cancel, :canceledAt, :grace, :pending, :test, :endedAt, :reason,
                                :createdAt, 0)
                            """).paramSource(params(s)).update();
            return s;
        }
        return new Subscription(
                s.id(),
                s.userId(),
                s.tier(),
                s.provider(),
                s.providerSubscriptionId(),
                s.providerCustomerId(),
                s.status(),
                s.periodStart(),
                s.periodEnd(),
                s.trialEndsAt(),
                s.trialEndingNotified(),
                s.cancelAtPeriodEnd(),
                s.canceledAt(),
                s.graceUntil(),
                s.pendingTier(),
                s.testMode(),
                s.endedAt(),
                s.endReason(),
                s.createdAt(),
                s.version() + 1);
    }

    @Override
    public List<UUID> findTimerCandidates(Instant horizon, int limit) {
        return jdbc.sql("""
                        SELECT id FROM subscription
                        WHERE (status = 'TRIALING' AND trial_ends_at <= :horizon)
                           OR (status = 'CANCELED' AND current_period_end <= :horizon)
                           OR (status = 'PAST_DUE' AND grace_until <= :horizon)
                           OR (status = 'ACTIVE' AND (provider = 'MANUAL' OR provider_subscription_id IS NULL)
                               AND current_period_end <= :horizon)
                        ORDER BY current_period_end LIMIT :limit
                        """)
                .param("horizon", Sql.ts(horizon))
                .param("limit", limit)
                .query(UUID.class)
                .list();
    }

    @Override
    public Page page(
            @Nullable SubscriptionStatus status,
            @Nullable PlanTierCode tier,
            @Nullable PaymentProviderCode provider,
            @Nullable Instant beforeCreatedAt,
            @Nullable UUID beforeId,
            int limit) {
        List<Subscription> rows = jdbc.sql("SELECT " + COLUMNS + """
                         FROM subscription
                        WHERE (CAST(:status AS varchar) IS NULL OR status = :status)
                          AND (CAST(:tier AS varchar) IS NULL OR plan_tier_code = :tier)
                          AND (CAST(:provider AS varchar) IS NULL OR provider = :provider)
                          AND (CAST(:before AS timestamptz) IS NULL OR (created_at, id) < (:before, :beforeId))
                        ORDER BY created_at DESC, id DESC LIMIT :limit
                        """)
                .param("status", Sql.name(status))
                .param("tier", Sql.name(tier))
                .param("provider", Sql.name(provider))
                .param("before", Sql.ts(beforeCreatedAt))
                .param("beforeId", beforeId == null ? new UUID(0, 0) : beforeId)
                .param("limit", limit + 1)
                .query(JdbcSubscriptionRepositoryAdapter::map)
                .list();
        boolean more = rows.size() > limit;
        return new Page(more ? rows.subList(0, limit) : rows, more);
    }

    @Override
    public void deleteByUser(UUID userId) {
        jdbc.sql("DELETE FROM seat WHERE subscription_id IN (SELECT id FROM subscription WHERE user_id = :id)")
                .param("id", userId)
                .update();
        jdbc.sql("UPDATE payment SET subscription_id = NULL WHERE subscription_id IN "
                        + "(SELECT id FROM subscription WHERE user_id = :id)")
                .param("id", userId)
                .update();
        jdbc.sql("DELETE FROM subscription WHERE user_id = :id")
                .param("id", userId)
                .update();
    }

    private Optional<Subscription> one(String where, UUID id, boolean lock) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM subscription " + where + (lock ? " FOR UPDATE" : ""))
                .param("id", id)
                .query(JdbcSubscriptionRepositoryAdapter::map)
                .optional();
    }

    private static org.springframework.jdbc.core.namedparam.MapSqlParameterSource params(Subscription s) {
        return new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("id", s.id())
                .addValue("user", s.userId())
                .addValue("tier", s.tier().name())
                .addValue("provider", Sql.name(s.provider()))
                .addValue("ref", s.providerSubscriptionId())
                .addValue("customer", s.providerCustomerId())
                .addValue("status", s.status().name())
                .addValue("start", Sql.ts(s.periodStart()))
                .addValue("end", Sql.ts(s.periodEnd()))
                .addValue("trialEnds", Sql.ts(s.trialEndsAt()))
                .addValue("notified", s.trialEndingNotified())
                .addValue("cancel", s.cancelAtPeriodEnd())
                .addValue("canceledAt", Sql.ts(s.canceledAt()))
                .addValue("grace", Sql.ts(s.graceUntil()))
                .addValue("pending", Sql.name(s.pendingTier()))
                .addValue("test", s.testMode())
                .addValue("endedAt", Sql.ts(s.endedAt()))
                .addValue("reason", Sql.name(s.endReason()))
                .addValue("createdAt", Sql.ts(s.createdAt()))
                .addValue("version", s.version());
    }

    static Subscription map(ResultSet rs, int row) throws SQLException {
        return new Subscription(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                PlanTierCode.valueOf(rs.getString("plan_tier_code")),
                Sql.enumOf(PaymentProviderCode.class, rs.getString("provider")),
                rs.getString("provider_subscription_id"),
                rs.getString("provider_customer_id"),
                SubscriptionStatus.valueOf(rs.getString("status")),
                Sql.requiredInstant(rs, "current_period_start"),
                Sql.requiredInstant(rs, "current_period_end"),
                Sql.instant(rs, "trial_ends_at"),
                rs.getBoolean("trial_ending_notified"),
                rs.getBoolean("cancel_at_period_end"),
                Sql.instant(rs, "canceled_at"),
                Sql.instant(rs, "grace_until"),
                Sql.enumOf(PlanTierCode.class, rs.getString("pending_plan_tier_code")),
                rs.getBoolean("test_mode"),
                Sql.instant(rs, "ended_at"),
                Sql.enumOf(EndReason.class, rs.getString("end_reason")),
                Sql.requiredInstant(rs, "created_at"),
                rs.getInt("version"));
    }
}
