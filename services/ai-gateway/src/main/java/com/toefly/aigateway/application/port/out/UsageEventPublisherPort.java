package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.call.AiCall;

/** Writes {@code toefly.ai-gateway.usage-recorded.v1} to the outbox, in the transaction of the journal row. */
public interface UsageEventPublisherPort {

    void usageRecorded(AiCall call);
}
