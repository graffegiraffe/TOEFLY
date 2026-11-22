package com.toefly.billing.domain.saga;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What the confirmed payment carries into the saga, plus the ids the steps create on the way. */
public record PaymentSagaData(
        @Nullable UUID checkoutSessionId,
        String providerPaymentId,
        @Nullable String providerSubscriptionId,
        @Nullable String providerCustomerId,
        @Nullable Integer amountMinor,
        @Nullable String currency,
        Instant paidAt,
        Instant periodStart,
        Instant periodEnd,
        @Nullable String receiptUrl,
        boolean testMode,
        @Nullable UUID paymentId,
        @Nullable UUID subscriptionId,
        boolean replacedTrial) {

    public PaymentSagaData {
        Objects.requireNonNull(providerPaymentId, "providerPaymentId");
        Objects.requireNonNull(paidAt, "paidAt");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
    }

    public PaymentSagaData withPayment(UUID payment) {
        return new PaymentSagaData(
                checkoutSessionId,
                providerPaymentId,
                providerSubscriptionId,
                providerCustomerId,
                amountMinor,
                currency,
                paidAt,
                periodStart,
                periodEnd,
                receiptUrl,
                testMode,
                payment,
                subscriptionId,
                replacedTrial);
    }

    public PaymentSagaData withSubscription(UUID subscription, boolean trialReplaced) {
        return new PaymentSagaData(
                checkoutSessionId,
                providerPaymentId,
                providerSubscriptionId,
                providerCustomerId,
                amountMinor,
                currency,
                paidAt,
                periodStart,
                periodEnd,
                receiptUrl,
                testMode,
                paymentId,
                subscription,
                trialReplaced);
    }
}
