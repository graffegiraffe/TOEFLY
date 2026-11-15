package com.toefly.aigateway.application.port.in;

import com.toefly.aigateway.domain.quota.QuotaDimension;
import com.toefly.aigateway.domain.routing.ModelRef;
import java.util.List;

/** Snapshot of the buckets and breakers ({@code /internal/v1/ai/quotas}, {@code /admin/ai/providers}). */
public interface QuotaQueryUseCase {

    List<ModelStatus> snapshot();

    record Dimension(QuotaDimension dimension, long capacity, double remaining) {}

    record ModelStatus(ModelRef model, List<Dimension> dimensions, String circuit) {}
}
