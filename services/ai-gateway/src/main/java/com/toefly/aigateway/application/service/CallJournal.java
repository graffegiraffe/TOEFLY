package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.out.AiCallRepository;
import com.toefly.aigateway.application.port.out.PriceListPort;
import com.toefly.aigateway.application.port.out.UsageEventPublisherPort;
import com.toefly.aigateway.domain.call.AiCall;
import com.toefly.aigateway.domain.call.AiCallIds;
import com.toefly.aigateway.domain.call.AiCallOutcome;
import com.toefly.aigateway.domain.call.TokenUsage;
import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.routing.AiUseCase;
import com.toefly.aigateway.domain.routing.ModelRef;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes the journal row, the daily aggregate and {@code usage-recorded} in one transaction (§2.7), for every call:
 * answers, cache hits and refusals.
 */
@Component
class CallJournal {

    /** What a service knows about a finished call. */
    record Entry(
            String callerService,
            AiUseCase useCase,
            @Nullable String feature,
            @Nullable Priority priority,
            @Nullable UUID userId,
            @Nullable String promptKey,
            @Nullable Integer promptVersion,
            ModelRef model,
            int fallbackDepth,
            boolean degraded,
            boolean cacheHit,
            @Nullable String requestMasked,
            @Nullable String responseMasked,
            TokenUsage usage,
            long latencyMs,
            @Nullable String guardVerdict,
            AiCallOutcome outcome,
            @Nullable String errorCode,
            @Nullable UUID correlationId) {}

    private final AiCallRepository calls;
    private final UsageEventPublisherPort events;
    private final PriceListPort prices;
    private final TransactionTemplate transactions;
    private final MeterRegistry meters;
    private final Clock clock;

    CallJournal(
            AiCallRepository calls,
            UsageEventPublisherPort events,
            PriceListPort prices,
            PlatformTransactionManager transactionManager,
            MeterRegistry meters,
            Clock clock) {
        this.calls = calls;
        this.events = events;
        this.prices = prices;
        this.transactions = new TransactionTemplate(transactionManager);
        this.meters = meters;
        this.clock = clock;
    }

    String nextId() {
        return AiCallIds.next(clock.instant());
    }

    AiCall record(String id, Entry entry) {
        Instant now = clock.instant();
        long cost = entry.cacheHit() ? 0 : prices.price(entry.model()).microUsd(entry.usage());
        AiCall call = new AiCall(
                id,
                entry.callerService(),
                entry.useCase(),
                entry.feature(),
                entry.priority(),
                entry.userId(),
                entry.promptKey(),
                entry.promptVersion(),
                entry.model().provider(),
                entry.model().model(),
                entry.fallbackDepth(),
                entry.degraded(),
                entry.cacheHit(),
                entry.requestMasked(),
                entry.responseMasked(),
                entry.usage(),
                entry.latencyMs(),
                cost,
                entry.guardVerdict(),
                entry.outcome(),
                entry.errorCode(),
                MDC.get("traceId"),
                entry.correlationId(),
                now);
        transactions.executeWithoutResult(status -> {
            calls.save(call);
            events.usageRecorded(call);
        });
        meters.counter(
                        "toefly.ai.calls",
                        "use_case",
                        entry.useCase().name(),
                        "provider",
                        call.provider().name(),
                        "outcome",
                        entry.outcome().name())
                .increment();
        meters.counter("toefly.ai.tokens", "model", entry.model().toString(), "direction", "input")
                .increment(entry.usage().inputTokens());
        meters.counter("toefly.ai.tokens", "model", entry.model().toString(), "direction", "output")
                .increment(entry.usage().outputTokens());
        return call;
    }
}
