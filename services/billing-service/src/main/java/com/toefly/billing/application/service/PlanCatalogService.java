package com.toefly.billing.application.service;

import com.toefly.billing.application.port.in.PlanCatalogUseCase;
import com.toefly.billing.application.port.out.PlanTierRepository;
import org.springframework.stereotype.Service;

/** AC-27.01.1, AC-27.01.3, AC-27.08.3: prices and rights from PlanTier; web providers and their mode. */
@Service
public class PlanCatalogService implements PlanCatalogUseCase {

    private final PlanTierRepository tiers;
    private final PaymentProviders providers;
    private final BillingPolicy policy;

    public PlanCatalogService(PlanTierRepository tiers, PaymentProviders providers, BillingPolicy policy) {
        this.tiers = tiers;
        this.providers = providers;
        this.policy = policy;
    }

    @Override
    public Catalog catalog() {
        return new Catalog(
                tiers.findAll(),
                policy.webProviders().stream()
                        .flatMap(code -> providers.find(code).stream())
                        .map(provider -> new ProviderOption(provider.code(), provider.testMode()))
                        .toList());
    }
}
