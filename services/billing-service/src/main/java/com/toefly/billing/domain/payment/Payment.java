package com.toefly.billing.domain.payment;

import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A charge confirmed or declined by a provider; amounts never change (INV-BIL-05). */
public record Payment(
        UUID id,
        @Nullable UUID subscriptionId,
        @Nullable UUID userId,
        PlanTierCode tier,
        PaymentProviderCode provider,
        String providerPaymentId,
        @Nullable Integer amountMinor,
        @Nullable String currency,
        PaymentStatus status,
        @Nullable Instant paidAt,
        @Nullable String failureReason,
        @Nullable String receiptUrl,
        boolean testMode,
        Instant createdAt) {

    public Payment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerPaymentId, "providerPaymentId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Payment succeeded(
            UUID id,
            @Nullable UUID subscriptionId,
            UUID userId,
            PlanTierCode tier,
            PaymentProviderCode provider,
            String providerPaymentId,
            @Nullable Integer amountMinor,
            @Nullable String currency,
            Instant paidAt,
            @Nullable String receiptUrl,
            boolean testMode) {
        return new Payment(
                id,
                subscriptionId,
                userId,
                tier,
                provider,
                providerPaymentId,
                amountMinor,
                currency,
                PaymentStatus.SUCCEEDED,
                paidAt,
                null,
                receiptUrl,
                testMode,
                paidAt);
    }

    public static Payment failed(
            UUID id,
            @Nullable UUID subscriptionId,
            UUID userId,
            PlanTierCode tier,
            PaymentProviderCode provider,
            String providerPaymentId,
            @Nullable Integer amountMinor,
            @Nullable String currency,
            String reason,
            boolean testMode,
            Instant now) {
        return new Payment(
                id,
                subscriptionId,
                userId,
                tier,
                provider,
                providerPaymentId,
                amountMinor,
                currency,
                PaymentStatus.FAILED,
                null,
                reason,
                null,
                testMode,
                now);
    }

    public Payment forSubscription(UUID subscription) {
        return new Payment(
                id,
                subscription,
                userId,
                tier,
                provider,
                providerPaymentId,
                amountMinor,
                currency,
                status,
                paidAt,
                failureReason,
                receiptUrl,
                testMode,
                createdAt);
    }

    public Payment refunded() {
        return new Payment(
                id,
                subscriptionId,
                userId,
                tier,
                provider,
                providerPaymentId,
                amountMinor,
                currency,
                PaymentStatus.REFUNDED,
                paidAt,
                failureReason,
                receiptUrl,
                testMode,
                createdAt);
    }

    /** INV-BIL-05: after an erasure the record stays for accounting without the person. */
    public Payment anonymized() {
        return new Payment(
                id,
                subscriptionId,
                null,
                tier,
                provider,
                providerPaymentId,
                amountMinor,
                currency,
                status,
                paidAt,
                failureReason,
                receiptUrl,
                testMode,
                createdAt);
    }
}
