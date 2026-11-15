package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.out.ProviderQuotaRepository;
import com.toefly.aigateway.domain.quota.ProviderQuota;
import com.toefly.aigateway.domain.quota.QuotaDimension;
import com.toefly.aigateway.domain.routing.ModelRef;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Applies the limits a provider announced in its headers when they are lower than the stored ones (§2.5): Groq's
 * {@code x-ratelimit-limit-requests} is the RPD, {@code x-ratelimit-limit-tokens} the TPM.
 */
@Component
class ProviderLimits {

    private final ProviderQuotaRepository quotas;

    ProviderLimits(ProviderQuotaRepository quotas) {
        this.quotas = quotas;
    }

    void apply(ModelRef model, Map<String, Long> reported) {
        if (reported.isEmpty()) {
            return;
        }
        ProviderQuota current = quotas.find(model);
        ProviderQuota lowered = current;
        for (Map.Entry<String, Long> limit : reported.entrySet()) {
            lowered = lowered.lowered(QuotaDimension.valueOf(limit.getKey()), limit.getValue());
        }
        if (lowered != current) {
            quotas.save(lowered);
        }
    }
}
