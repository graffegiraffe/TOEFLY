package com.toefly.aigateway.adapter.out.messaging;

import com.toefly.aigateway.application.port.out.UsageEventPublisherPort;
import com.toefly.aigateway.domain.call.AiCall;
import com.toefly.common.outbox.IntegrationEvent;
import com.toefly.common.outbox.IntegrationEventPublisher;
import com.toefly.events.aigateway.AiCallOutcome;
import com.toefly.events.aigateway.UsageRecordedV1;
import com.toefly.events.common.AiProvider;
import java.util.UUID;

/** {@code toefly.ai-gateway.usage-recorded.v1} through the outbox (§2.7): key userId, or aiCallId for system calls. */
public class OutboxUsageEventPublisherAdapter implements UsageEventPublisherPort {

    static final String USAGE_RECORDED = "toefly.ai-gateway.usage-recorded.v1";

    private final IntegrationEventPublisher publisher;

    public OutboxUsageEventPublisherAdapter(IntegrationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void usageRecorded(AiCall call) {
        UUID eventId = UUID.randomUUID();
        UsageRecordedV1 payload = UsageRecordedV1.newBuilder()
                .setEventId(eventId)
                .setOccurredAt(call.createdAt())
                .setUserId(call.userId())
                .setCorrelationId(call.correlationId())
                .setCausationId(null)
                .setAiCallId(call.id())
                .setCallerService(call.callerService())
                .setFeature(call.feature())
                .setUseCase(call.useCase().name())
                .setProvider(AiProvider.valueOf(call.provider().name()))
                .setModel(call.model())
                .setPromptKey(call.promptKey())
                .setPromptVersion(call.promptVersion())
                .setInputTokens(call.usage().inputTokens())
                .setOutputTokens(call.usage().outputTokens())
                .setAudioSeconds(call.usage().audioSeconds())
                .setCharacters(call.usage().characters())
                .setShadowCostMicroUsd(call.shadowCostMicroUsd())
                .setCacheHit(call.cacheHit())
                .setFallbackDepth(call.fallbackDepth())
                .setDegraded(call.degraded())
                .setLatencyMs((int) Math.min(Integer.MAX_VALUE, call.latencyMs()))
                .setOutcome(AiCallOutcome.valueOf(call.outcome().name()))
                .build();
        publisher.publish(new IntegrationEvent(
                eventId,
                USAGE_RECORDED,
                "AiCall",
                call.id(),
                call.userId() == null ? call.id() : call.userId().toString(),
                "ai-calls/" + call.id(),
                call.createdAt(),
                call.userId(),
                call.correlationId(),
                null,
                payload));
    }
}
