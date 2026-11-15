package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.in.QuotaQueryUseCase;
import com.toefly.aigateway.application.port.out.ProviderQuotaRepository;
import com.toefly.aigateway.application.port.out.QuotaStorePort;
import com.toefly.aigateway.application.port.out.ResiliencePort;
import com.toefly.aigateway.application.port.out.RoutingTablePort;
import com.toefly.aigateway.domain.quota.ProviderQuota;
import com.toefly.aigateway.domain.quota.QuotaDimension;
import com.toefly.aigateway.domain.quota.QuotaLimit;
import com.toefly.aigateway.domain.routing.ModelRef;
import com.toefly.aigateway.domain.routing.ModelRoute;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Remaining quota and breaker state of every model the routes use. */
@Service
class QuotaQueryService implements QuotaQueryUseCase {

    private final RoutingTablePort routing;
    private final ProviderQuotaRepository quotas;
    private final QuotaStorePort store;
    private final ResiliencePort resilience;

    QuotaQueryService(
            RoutingTablePort routing, ProviderQuotaRepository quotas, QuotaStorePort store, ResiliencePort resilience) {
        this.routing = routing;
        this.quotas = quotas;
        this.store = store;
        this.resilience = resilience;
    }

    @Override
    public List<ModelStatus> snapshot() {
        Set<ModelRef> models = new LinkedHashSet<>();
        for (ModelRoute route : routing.current().all()) {
            models.addAll(route.candidates());
        }
        quotas.all().forEach(quota -> models.add(quota.model()));
        Map<String, String> circuits = resilience.states();
        List<ModelStatus> statuses = new ArrayList<>();
        for (ModelRef model : models) {
            ProviderQuota quota = quotas.find(model);
            Map<QuotaDimension, Double> remaining = quota.limits().isEmpty() ? Map.of() : store.remaining(quota);
            List<Dimension> dimensions = new ArrayList<>();
            for (QuotaLimit limit : quota.limits()) {
                dimensions.add(new Dimension(
                        limit.dimension(), limit.capacity(), remaining.getOrDefault(limit.dimension(), (double)
                                limit.capacity())));
            }
            dimensions.sort(Comparator.comparing(Dimension::dimension));
            statuses.add(new ModelStatus(model, dimensions, circuits.getOrDefault(model.toString(), "CLOSED")));
        }
        statuses.sort(Comparator.comparing(status -> status.model().toString()));
        return statuses;
    }
}
