package com.toefly.billing.application.port.out;

import com.toefly.billing.domain.plan.PlanTierCode;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A verified provider notification in provider-neutral terms; adapters map Stripe, bePaid and App Store events to it
 * so the same saga runs for all (ADR-0021). Only these fields are stored in {@code webhook_event.payload} (docs/10 §17.4).
 */
public sealed interface ProviderEvent {

    /** Id of the provider event; the idempotency key of the webhook. */
    String eventId();

    /** Type of the provider event, for the log and the store. */
    String type();

    /** The first payment of a checkout went through. */
    record CheckoutCompleted(
            String eventId,
            String type,
            @Nullable UUID checkoutSessionId,
            @Nullable String providerSessionId,
            @Nullable UUID userId,
            @Nullable PlanTierCode tier,
            String providerPaymentId,
            @Nullable String providerSubscriptionId,
            @Nullable String providerCustomerId,
            @Nullable Integer amountMinor,
            @Nullable String currency,
            Instant paidAt,
            @Nullable Instant periodStart,
            @Nullable Instant periodEnd,
            @Nullable String receiptUrl,
            boolean testMode)
            implements ProviderEvent {}

    /** The checkout payment was declined (AC-27.03.5). */
    record CheckoutFailed(
            String eventId,
            String type,
            @Nullable UUID checkoutSessionId,
            @Nullable String providerSessionId,
            @Nullable String providerPaymentId,
            String reason,
            @Nullable String message)
            implements ProviderEvent {}

    /** A renewal was paid (AC-27.07.1). */
    record RenewalPaid(
            String eventId,
            String type,
            String providerSubscriptionId,
            String providerPaymentId,
            @Nullable Integer amountMinor,
            @Nullable String currency,
            Instant paidAt,
            Instant periodStart,
            Instant periodEnd,
            @Nullable String receiptUrl,
            boolean testMode)
            implements ProviderEvent {}

    /** A renewal failed (AC-27.06.1). */
    record RenewalFailed(
            String eventId,
            String type,
            String providerSubscriptionId,
            @Nullable String providerPaymentId,
            String reason,
            @Nullable String message)
            implements ProviderEvent {}

    /** The provider ended the subscription (after its retries, or canceled in its console). */
    record SubscriptionEnded(String eventId, String type, String providerSubscriptionId) implements ProviderEvent {}

    /** A refund or chargeback made in the provider console. */
    record Refunded(String eventId, String type, String providerPaymentId) implements ProviderEvent {}

    /** An event billing does not act on; stored to answer repeated deliveries with 200. */
    record Ignored(String eventId, String type) implements ProviderEvent {}
}
