package com.toefly.billing.adapter.out.persistence;

import com.toefly.billing.application.port.out.PaymentRepository;
import com.toefly.billing.domain.payment.Payment;
import com.toefly.billing.domain.payment.PaymentStatus;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaymentRepositoryAdapter implements PaymentRepository {

    static final String COLUMNS = """
            id, subscription_id, user_id, plan_tier_code, provider, provider_payment_id, amount_minor, currency,
            status, paid_at, failure_reason, receipt_url, test_mode, created_at
            """;

    private final JdbcClient jdbc;

    public JdbcPaymentRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Payment insertIfAbsent(Payment p) {
        jdbc.sql("""
                        INSERT INTO payment (id, subscription_id, user_id, plan_tier_code, provider,
                            provider_payment_id, amount_minor, currency, status, paid_at, failure_reason, receipt_url,
                            test_mode, created_at)
                        VALUES (:id, :sub, :user, :tier, :provider, :ref, :amount, :currency, :status, :paidAt,
                            :reason, :receipt, :test, :createdAt)
                        ON CONFLICT (provider, provider_payment_id) DO NOTHING
                        """)
                .param("id", p.id())
                .param("sub", p.subscriptionId())
                .param("user", p.userId())
                .param("tier", p.tier().name())
                .param("provider", p.provider().name())
                .param("ref", p.providerPaymentId())
                .param("amount", p.amountMinor())
                .param("currency", p.currency())
                .param("status", p.status().name())
                .param("paidAt", Sql.ts(p.paidAt()))
                .param("reason", p.failureReason())
                .param("receipt", p.receiptUrl())
                .param("test", p.testMode())
                .param("createdAt", Sql.ts(p.createdAt()))
                .update();
        return findByProviderRef(p.provider(), p.providerPaymentId()).orElseThrow();
    }

    /** Only the links and the status change; amounts are immutable (INV-BIL-05). */
    @Override
    public void update(Payment p) {
        jdbc.sql("UPDATE payment SET subscription_id = :sub, user_id = :user, status = :status WHERE id = :id")
                .param("sub", p.subscriptionId())
                .param("user", p.userId())
                .param("status", p.status().name())
                .param("id", p.id())
                .update();
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM payment WHERE id = :id")
                .param("id", id)
                .query(JdbcPaymentRepositoryAdapter::map)
                .optional();
    }

    @Override
    public Optional<Payment> findByProviderRef(PaymentProviderCode provider, String providerPaymentId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM payment WHERE provider = :provider AND provider_payment_id = :ref")
                .param("provider", provider.name())
                .param("ref", providerPaymentId)
                .query(JdbcPaymentRepositoryAdapter::map)
                .optional();
    }

    @Override
    public List<Payment> findByUser(UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM payment WHERE user_id = :user ORDER BY created_at DESC, id")
                .param("user", userId)
                .query(JdbcPaymentRepositoryAdapter::map)
                .list();
    }

    @Override
    public void anonymize(UUID userId) {
        jdbc.sql("UPDATE payment SET user_id = NULL WHERE user_id = :user")
                .param("user", userId)
                .update();
    }

    static Payment map(ResultSet rs, int row) throws SQLException {
        return new Payment(
                rs.getObject("id", UUID.class),
                Sql.uuid(rs, "subscription_id"),
                Sql.uuid(rs, "user_id"),
                PlanTierCode.valueOf(rs.getString("plan_tier_code")),
                PaymentProviderCode.valueOf(rs.getString("provider")),
                rs.getString("provider_payment_id"),
                Sql.integer(rs, "amount_minor"),
                rs.getString("currency"),
                PaymentStatus.valueOf(rs.getString("status")),
                Sql.instant(rs, "paid_at"),
                rs.getString("failure_reason"),
                rs.getString("receipt_url"),
                rs.getBoolean("test_mode"),
                Sql.requiredInstant(rs, "created_at"));
    }
}
