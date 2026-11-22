package com.toefly.billing.adapter.out.persistence;

import com.toefly.billing.application.port.out.EntitlementRepository;
import com.toefly.billing.domain.entitlement.Entitlement;
import com.toefly.billing.domain.entitlement.EntitlementSet;
import com.toefly.billing.domain.entitlement.Grant;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEntitlementRepositoryAdapter implements EntitlementRepository {

    private final JdbcClient jdbc;

    public JdbcEntitlementRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A transaction-level advisory lock per user serializes recalculations, also before the first snapshot. */
    @Override
    public Optional<Snapshot> lockSnapshot(UUID userId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:user, 7))")
                .param("user", userId.toString())
                .query()
                .singleRow();
        return findSnapshot(userId);
    }

    @Override
    public Optional<Snapshot> findSnapshot(UUID userId) {
        return jdbc.sql("SELECT version, fingerprint, changed_at FROM entitlement_snapshot WHERE user_id = :user")
                .param("user", userId)
                .query((rs, row) -> new Snapshot(
                        rs.getLong("version"),
                        rs.getString("fingerprint").strip(),
                        rs.getTimestamp("changed_at").toInstant()))
                .optional();
    }

    @Override
    public void store(EntitlementSet set, long version, Instant changedAt) {
        jdbc.sql("""
                        INSERT INTO entitlement_snapshot (user_id, version, plan_tier_code, entitlements, fingerprint,
                            tutor_id, trial_ends_at, changed_at)
                        VALUES (:user, :version, :tier, :entitlements, :fingerprint, :tutor, :trial, :changedAt)
                        ON CONFLICT (user_id) DO UPDATE SET version = EXCLUDED.version,
                            plan_tier_code = EXCLUDED.plan_tier_code, entitlements = EXCLUDED.entitlements,
                            fingerprint = EXCLUDED.fingerprint, tutor_id = EXCLUDED.tutor_id,
                            trial_ends_at = EXCLUDED.trial_ends_at, changed_at = EXCLUDED.changed_at
                        """)
                .param("user", set.userId())
                .param("version", version)
                .param("tier", set.planTierCode().name())
                .param(
                        "entitlements",
                        set.entitlements().stream()
                                .map(Entitlement::name)
                                .sorted()
                                .toArray(String[]::new))
                .param("fingerprint", set.fingerprint())
                .param("tutor", set.tutorId())
                .param("trial", Sql.ts(set.trialEndsAt()))
                .param("changedAt", Timestamp.from(changedAt))
                .update();
        jdbc.sql("DELETE FROM entitlement_grant WHERE user_id = :user")
                .param("user", set.userId())
                .update();
        for (Grant grant : set.grants()) {
            jdbc.sql("""
                            INSERT INTO entitlement_grant (id, user_id, entitlement, source, source_id, grant_limit,
                                used, valid_until)
                            VALUES (:id, :user, :entitlement, :source, :sourceId, :limit, :used, :validUntil)
                            """)
                    .param("id", UUID.randomUUID())
                    .param("user", set.userId())
                    .param("entitlement", grant.entitlement().name())
                    .param("source", grant.source().name())
                    .param("sourceId", grant.sourceId())
                    .param("limit", grant.limit())
                    .param("used", grant.used())
                    .param("validUntil", Sql.ts(grant.validUntil()))
                    .update();
        }
    }

    @Override
    public int freeUsed(UUID userId) {
        return jdbc.sql("SELECT used FROM free_ai_usage WHERE user_id = :user")
                .param("user", userId)
                .query(Integer.class)
                .optional()
                .orElse(0);
    }

    @Override
    public void countFreeCheck(UUID userId, Instant now) {
        jdbc.sql("""
                        INSERT INTO free_ai_usage (user_id, used, updated_at) VALUES (:user, 1, :now)
                        ON CONFLICT (user_id) DO UPDATE SET used = free_ai_usage.used + 1, updated_at = :now
                        """).param("user", userId).param("now", Timestamp.from(now)).update();
    }

    @Override
    public void deleteUser(UUID userId) {
        for (String table : new String[] {"entitlement_grant", "entitlement_snapshot", "free_ai_usage"}) {
            jdbc.sql("DELETE FROM " + table + " WHERE user_id = :user")
                    .param("user", userId)
                    .update();
        }
    }
}
