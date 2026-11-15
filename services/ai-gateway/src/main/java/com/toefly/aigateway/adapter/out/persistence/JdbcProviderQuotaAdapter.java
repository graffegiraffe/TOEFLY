package com.toefly.aigateway.adapter.out.persistence;

import com.toefly.aigateway.application.port.out.ProviderQuotaRepository;
import com.toefly.aigateway.domain.quota.ProviderQuota;
import com.toefly.aigateway.domain.quota.QuotaDimension;
import com.toefly.aigateway.domain.quota.QuotaLimit;
import com.toefly.aigateway.domain.routing.AiProvider;
import com.toefly.aigateway.domain.routing.ModelRef;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Limits of the models ({@code provider_quota}), read on every admission through a one-minute in-memory copy so a
 * change from the admin reaches all replicas within a minute.
 */
public class JdbcProviderQuotaAdapter implements ProviderQuotaRepository {

    private static final Duration FRESH = Duration.ofMinutes(1);

    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;
    private volatile Map<ModelRef, ProviderQuota> cached = Map.of();
    private volatile Instant loadedAt = Instant.EPOCH;

    public JdbcProviderQuotaAdapter(org.springframework.jdbc.core.simple.JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ProviderQuota find(ModelRef model) {
        ProviderQuota quota = snapshot().get(model);
        return quota == null ? ProviderQuota.unlimited(model) : quota;
    }

    @Override
    public List<ProviderQuota> all() {
        return List.copyOf(snapshot().values());
    }

    @Override
    public void save(ProviderQuota quota) {
        for (QuotaLimit limit : quota.limits()) {
            jdbc.sql("""
                            INSERT INTO provider_quota (provider, model, dimension, capacity, updated_at)
                            VALUES (:provider, :model, :dimension, :capacity, now())
                            ON CONFLICT (provider, model, dimension)
                            DO UPDATE SET capacity = EXCLUDED.capacity, updated_at = now()""")
                    .param("provider", quota.model().provider().name())
                    .param("model", quota.model().model())
                    .param("dimension", limit.dimension().name())
                    .param("capacity", limit.capacity())
                    .update();
        }
        loadedAt = Instant.EPOCH;
    }

    /** Adds limits a model does not have yet; limits already stored (possibly edited) stay. */
    public void seed(List<ProviderQuota> defaults) {
        for (ProviderQuota quota : defaults) {
            for (QuotaLimit limit : quota.limits()) {
                jdbc.sql("""
                                INSERT INTO provider_quota (provider, model, dimension, capacity)
                                VALUES (:provider, :model, :dimension, :capacity)
                                ON CONFLICT DO NOTHING""")
                        .param("provider", quota.model().provider().name())
                        .param("model", quota.model().model())
                        .param("dimension", limit.dimension().name())
                        .param("capacity", limit.capacity())
                        .update();
            }
        }
        loadedAt = Instant.EPOCH;
    }

    private Map<ModelRef, ProviderQuota> snapshot() {
        if (Instant.now().isAfter(loadedAt.plus(FRESH))) {
            Map<ModelRef, List<QuotaLimit>> limits = new LinkedHashMap<>();
            jdbc.sql("SELECT provider, model, dimension, capacity FROM provider_quota ORDER BY provider, model")
                    .query(rs -> {
                        ModelRef model =
                                new ModelRef(AiProvider.valueOf(rs.getString("provider")), rs.getString("model"));
                        limits.computeIfAbsent(model, m -> new ArrayList<>())
                                .add(new QuotaLimit(
                                        QuotaDimension.valueOf(rs.getString("dimension")), rs.getLong("capacity")));
                    });
            Map<ModelRef, ProviderQuota> fresh = new LinkedHashMap<>();
            limits.forEach((model, list) -> fresh.put(model, new ProviderQuota(model, list)));
            cached = fresh;
            loadedAt = Instant.now();
        }
        return cached;
    }
}
