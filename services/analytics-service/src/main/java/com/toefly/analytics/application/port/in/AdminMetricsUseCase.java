package com.toefly.analytics.application.port.in;

import com.toefly.analytics.domain.metrics.AiUsageReport;
import com.toefly.analytics.domain.metrics.ProductReport;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Product metrics and AI spend for the admin (docs/01 §7). */
public interface AdminMetricsUseCase {

    ProductReport product(@Nullable LocalDate from, @Nullable LocalDate to);

    AiUsageReport aiUsage(@Nullable LocalDate from, @Nullable LocalDate to);
}
