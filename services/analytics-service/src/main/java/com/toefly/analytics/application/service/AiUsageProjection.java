package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.event.AnalyticsEvent;
import com.toefly.analytics.application.port.in.event.UsageRecorded;
import com.toefly.analytics.application.port.out.AiUsageRepository;
import com.toefly.analytics.domain.metrics.AiUsage;
import org.springframework.stereotype.Component;

/** {@code AiUsageDaily}: tokens, shadow cost and fallback calls of ai-gateway by day, provider, model and prompt. */
@Component
class AiUsageProjection implements Projection {

    private final AiUsageRepository usage;

    AiUsageProjection(AiUsageRepository usage) {
        this.usage = usage;
    }

    @Override
    public void apply(AnalyticsEvent event) {
        if (event instanceof UsageRecorded e) {
            usage.add(new AiUsage(
                    ProductMetricsProjection.day(e.at()),
                    e.provider(),
                    e.model(),
                    e.promptKey() == null ? AiUsage.NO_PROMPT : e.promptKey(),
                    e.inputTokens(),
                    e.outputTokens(),
                    e.costMicroUsd(),
                    1,
                    e.fallbackDepth() > 0 ? 1 : 0,
                    e.cacheHit() ? 1 : 0));
        }
    }

    @Override
    public void reset() {
        usage.reset();
    }
}
