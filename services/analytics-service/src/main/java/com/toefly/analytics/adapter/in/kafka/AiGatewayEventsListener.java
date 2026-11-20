package com.toefly.analytics.adapter.in.kafka;

import com.toefly.analytics.application.port.in.RecordEventUseCase;
import com.toefly.analytics.application.port.in.event.EventMeta;
import com.toefly.analytics.application.port.in.event.UsageRecorded;
import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.aigateway.UsageRecordedV1;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/** Every AI call with its tokens and shadow cost: the AI spend of the admin. */
@Component
public class AiGatewayEventsListener {

    private final ProcessedEventGuard guard;
    private final RecordEventUseCase recorder;
    private final ConsistencyLag lag;

    public AiGatewayEventsListener(ProcessedEventGuard guard, RecordEventUseCase recorder, ConsistencyLag lag) {
        this.guard = guard;
        this.recorder = recorder;
        this.lag = lag;
    }

    @ToeflyListener(id = "usage-recorded", topics = "toefly.ai-gateway.usage-recorded.v1")
    void onUsage(ConsumerRecord<String, UsageRecordedV1> record) {
        UsageRecordedV1 e = record.value();
        UsageRecorded event = new UsageRecorded(
                e.getUserId(),
                e.getProvider().name(),
                e.getModel(),
                e.getPromptKey(),
                e.getInputTokens(),
                e.getOutputTokens(),
                e.getShadowCostMicroUsd(),
                e.getFallbackDepth(),
                e.getCacheHit(),
                e.getOccurredAt());
        EventMeta meta = new EventMeta(e.getEventId(), record.topic(), e.getOccurredAt(), e.getUserId());
        lag.applied(
                guard.runOnce(record, "AiGatewayEventsListener.usage", () -> recorder.record(meta, event)),
                e.getOccurredAt());
    }
}
