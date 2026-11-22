package com.toefly.billing.application.port.in;

import com.toefly.billing.domain.plan.PlanTier;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.util.List;

public interface PlanCatalogUseCase {

    Catalog catalog();

    record Catalog(List<PlanTier> tiers, List<ProviderOption> providers) {}

    record ProviderOption(PaymentProviderCode code, boolean testMode) {}
}
