package com.toefly.billing.adapter.out.persistence;

import com.toefly.billing.application.port.out.PlanTierRepository;
import com.toefly.billing.domain.entitlement.Entitlement;
import com.toefly.billing.domain.plan.PlanTier;
import com.toefly.billing.domain.plan.PlanTierCode;
import java.sql.Array;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPlanTierRepositoryAdapter implements PlanTierRepository {

    private final JdbcClient jdbc;
    private volatile List<PlanTier> cache;

    public JdbcPlanTierRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PlanTier> findAll() {
        List<PlanTier> tiers = cache;
        if (tiers == null) {
            tiers = jdbc.sql("""
                            SELECT code, price_minor, currency, seat_limit, storage_bytes, trial_days,
                                free_ai_check_limit, entitlements
                            FROM plan_tier ORDER BY sort_order
                            """)
                    .query((rs, row) -> {
                        long storage = rs.getLong("storage_bytes");
                        Long storageBytes = rs.wasNull() ? null : storage;
                        return new PlanTier(
                                PlanTierCode.valueOf(rs.getString("code")),
                                rs.getInt("price_minor"),
                                rs.getString("currency"),
                                Sql.integer(rs, "seat_limit"),
                                storageBytes,
                                rs.getInt("trial_days"),
                                Sql.integer(rs, "free_ai_check_limit"),
                                entitlements(rs.getArray("entitlements")));
                    })
                    .list();
            cache = tiers;
        }
        return tiers;
    }

    static Set<Entitlement> entitlements(Array array) throws java.sql.SQLException {
        String[] names = (String[]) array.getArray();
        return Arrays.stream(names)
                .map(Entitlement::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Entitlement.class)));
    }
}
