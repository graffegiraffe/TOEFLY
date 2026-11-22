package com.toefly.billing.application.port.out;

import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WebhookEventRepository {

    /** INV-BIL-03: stores the event once; false for a repeated delivery. */
    boolean insertIfAbsent(PaymentProviderCode provider, ProviderEvent event, Instant receivedAt);

    /** Unprocessed events, oldest first, locked with {@code SKIP LOCKED}. */
    List<Stored> lockPending(int limit, Instant now);

    Optional<Stored> lock(PaymentProviderCode provider, String eventId);

    void markProcessed(PaymentProviderCode provider, String eventId, Instant now);

    void markFailed(PaymentProviderCode provider, String eventId, String error, Instant retryAfter);

    record Stored(PaymentProviderCode provider, ProviderEvent event, int attempts) {}
}
