package com.toefly.billing.adapter.out.persistence;

import com.toefly.billing.application.port.out.CheckoutSessionRepository;
import com.toefly.billing.domain.checkout.CheckoutSession;
import com.toefly.billing.domain.checkout.CheckoutStatus;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCheckoutSessionRepositoryAdapter implements CheckoutSessionRepository {

    static final String COLUMNS = """
            id, user_id, idempotency_key, plan_tier_code, provider, provider_session_id, redirect_url, return_url,
            status, test_mode, failure_reason, failure_message, saga_id, created_at, expires_at
            """;

    private final JdbcClient jdbc;

    public JdbcCheckoutSessionRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insert(CheckoutSession s) {
        return jdbc.sql("""
                                INSERT INTO checkout_session (id, user_id, idempotency_key, plan_tier_code, provider,
                                    provider_session_id, redirect_url, return_url, status, test_mode, failure_reason,
                                    failure_message, saga_id, created_at, expires_at)
                                VALUES (:id, :user, :key, :tier, :provider, :ref, :redirect, :returnUrl, :status,
                                    :test, :reason, :message, :saga, :createdAt, :expiresAt)
                                ON CONFLICT (user_id, idempotency_key) DO NOTHING
                                """)
                        .param("id", s.id())
                        .param("user", s.userId())
                        .param("key", s.idempotencyKey())
                        .param("tier", s.tier().name())
                        .param("provider", s.provider().name())
                        .param("ref", s.providerSessionId())
                        .param("redirect", s.redirectUrl())
                        .param("returnUrl", s.returnUrl())
                        .param("status", s.status().name())
                        .param("test", s.testMode())
                        .param("reason", s.failureReason())
                        .param("message", truncate(s.failureMessage()))
                        .param("saga", s.sagaId())
                        .param("createdAt", Sql.ts(s.createdAt()))
                        .param("expiresAt", Sql.ts(s.expiresAt()))
                        .update()
                == 1;
    }

    @Override
    public void update(CheckoutSession s) {
        jdbc.sql("""
                        UPDATE checkout_session SET provider_session_id = :ref, redirect_url = :redirect,
                            status = :status, failure_reason = :reason, failure_message = :message, saga_id = :saga,
                            expires_at = :expiresAt
                        WHERE id = :id
                        """)
                .param("ref", s.providerSessionId())
                .param("redirect", s.redirectUrl())
                .param("status", s.status().name())
                .param("reason", s.failureReason())
                .param("message", truncate(s.failureMessage()))
                .param("saga", s.sagaId())
                .param("expiresAt", Sql.ts(s.expiresAt()))
                .param("id", s.id())
                .update();
    }

    @Override
    public Optional<CheckoutSession> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM checkout_session WHERE id = :id")
                .param("id", id)
                .query(JdbcCheckoutSessionRepositoryAdapter::map)
                .optional();
    }

    @Override
    public Optional<CheckoutSession> findByKey(UUID userId, String idempotencyKey) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM checkout_session WHERE user_id = :user AND idempotency_key = :key")
                .param("user", userId)
                .param("key", idempotencyKey)
                .query(JdbcCheckoutSessionRepositoryAdapter::map)
                .optional();
    }

    @Override
    public Optional<CheckoutSession> findByProviderSession(PaymentProviderCode provider, String providerSessionId) {
        return jdbc.sql("SELECT " + COLUMNS
                        + " FROM checkout_session WHERE provider = :provider AND provider_session_id = :ref")
                .param("provider", provider.name())
                .param("ref", providerSessionId)
                .query(JdbcCheckoutSessionRepositoryAdapter::map)
                .optional();
    }

    @Override
    public void deleteByUser(UUID userId) {
        jdbc.sql("DELETE FROM checkout_session WHERE user_id = :user")
                .param("user", userId)
                .update();
    }

    private static @org.jspecify.annotations.Nullable String truncate(@org.jspecify.annotations.Nullable String text) {
        return text == null || text.length() <= 300 ? text : text.substring(0, 300);
    }

    static CheckoutSession map(ResultSet rs, int row) throws SQLException {
        return new CheckoutSession(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("idempotency_key"),
                PlanTierCode.valueOf(rs.getString("plan_tier_code")),
                PaymentProviderCode.valueOf(rs.getString("provider")),
                rs.getString("provider_session_id"),
                rs.getString("redirect_url"),
                rs.getString("return_url"),
                CheckoutStatus.valueOf(rs.getString("status")),
                rs.getBoolean("test_mode"),
                rs.getString("failure_reason"),
                rs.getString("failure_message"),
                Sql.uuid(rs, "saga_id"),
                Sql.requiredInstant(rs, "created_at"),
                Sql.requiredInstant(rs, "expires_at"));
    }
}
