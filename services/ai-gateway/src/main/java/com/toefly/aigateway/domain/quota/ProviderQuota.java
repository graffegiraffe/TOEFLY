package com.toefly.aigateway.domain.quota;

import com.toefly.aigateway.domain.routing.ModelRef;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Free-tier limits of one model (aggregate {@code ProviderQuota}, §2.5). A model without limits (Ollama) is bounded
 * only by its bulkhead.
 */
public record ProviderQuota(ModelRef model, List<QuotaLimit> limits) {

    public ProviderQuota {
        limits = List.copyOf(limits);
        if (limits.stream().map(QuotaLimit::dimension).distinct().count() != limits.size()) {
            throw new IllegalArgumentException("duplicate dimension for " + model);
        }
    }

    public static ProviderQuota unlimited(ModelRef model) {
        return new ProviderQuota(model, List.of());
    }

    public Optional<QuotaLimit> limit(QuotaDimension dimension) {
        return limits.stream().filter(l -> l.dimension() == dimension).findFirst();
    }

    /**
     * The provider reported a lower limit (response headers, §2.5): the lower one applies, a higher one is ignored.
     */
    public ProviderQuota lowered(QuotaDimension dimension, long reported) {
        Map<QuotaDimension, QuotaLimit> byDimension = new EnumMap<>(QuotaDimension.class);
        limits.forEach(l -> byDimension.put(l.dimension(), l));
        QuotaLimit current = byDimension.get(dimension);
        if (current == null || reported <= 0 || reported >= current.capacity()) {
            return this;
        }
        byDimension.put(dimension, new QuotaLimit(dimension, reported));
        return new ProviderQuota(model, List.copyOf(byDimension.values()));
    }
}
