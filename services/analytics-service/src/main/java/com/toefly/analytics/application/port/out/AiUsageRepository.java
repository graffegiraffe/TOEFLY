package com.toefly.analytics.application.port.out;

import com.toefly.analytics.domain.metrics.AiUsage;
import java.time.LocalDate;
import java.util.List;

/** {@code ai_usage_daily}: AI calls by day, provider, model and prompt, without identifiers of users. */
public interface AiUsageRepository {

    void add(AiUsage usage);

    List<AiUsage> rows(LocalDate from, LocalDate to);

    void reset();
}
