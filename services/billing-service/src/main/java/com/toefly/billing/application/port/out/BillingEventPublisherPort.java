package com.toefly.billing.application.port.out;

import com.toefly.billing.domain.entitlement.EntitlementSet;
import com.toefly.billing.domain.payment.Payment;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.EndReason;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import com.toefly.billing.domain.subscription.Subscription;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Events of billing through the transactional outbox (docs/00-README.md §8, docs/05 §8.5.5). */
public interface BillingEventPublisherPort {

    List<String> topics();

    void subscriptionActivated(Subscription subscription, @Nullable Integer seatLimit, Instant at, Cause cause);

    void subscriptionCanceled(Subscription subscription, EndReason reason, Instant at, Cause cause);

    void entitlementsChanged(EntitlementSet set, long version, Instant changedAt, Cause cause);

    /** Compacted topic: a tombstone after an erasure. */
    void entitlementsErased(UUID userId);

    void paymentSucceeded(Payment payment, Instant periodStart, Instant periodEnd, boolean renewal, Cause cause);

    void paymentFailed(
            UUID userId,
            @Nullable UUID paymentId,
            @Nullable UUID subscriptionId,
            PlanTierCode tier,
            PaymentProviderCode provider,
            String reason,
            @Nullable String message,
            @Nullable Instant graceUntil,
            Instant at,
            Cause cause);

    void trialEnding(Subscription trial, Instant at, Cause cause);
}
