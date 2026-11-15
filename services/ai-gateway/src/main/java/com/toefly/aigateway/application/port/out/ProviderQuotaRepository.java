package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.quota.ProviderQuota;
import com.toefly.aigateway.domain.routing.ModelRef;
import java.util.List;

/** Limits of the models (table {@code provider_quota}). A model without a row has no limits. */
public interface ProviderQuotaRepository {

    ProviderQuota find(ModelRef model);

    List<ProviderQuota> all();

    void save(ProviderQuota quota);
}
