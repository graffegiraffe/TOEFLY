package com.toefly.billing.domain.checkout;

import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A payment session at a provider; the result page polls it (AC-27.03.4, AC-27.03.5). */
public record CheckoutSession(
        UUID id,
        UUID userId,
        String idempotencyKey,
        PlanTierCode tier,
        PaymentProviderCode provider,
        @Nullable String providerSessionId,
        @Nullable String redirectUrl,
        String returnUrl,
        CheckoutStatus status,
        boolean testMode,
        @Nullable String failureReason,
        @Nullable String failureMessage,
        @Nullable UUID sagaId,
        Instant createdAt,
        Instant expiresAt) {

    public CheckoutSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(status, "status");
    }

    public static CheckoutSession open(
            UUID id,
            UUID userId,
            String idempotencyKey,
            PlanTierCode tier,
            PaymentProviderCode provider,
            String returnUrl,
            boolean testMode,
            Instant now,
            Instant expiresAt) {
        return new CheckoutSession(
                id,
                userId,
                idempotencyKey,
                tier,
                provider,
                null,
                null,
                returnUrl,
                CheckoutStatus.OPEN,
                testMode,
                null,
                null,
                null,
                now,
                expiresAt);
    }

    public CheckoutSession redirect(String providerSession, String url, Instant expires) {
        return new CheckoutSession(
                id,
                userId,
                idempotencyKey,
                tier,
                provider,
                providerSession,
                url,
                returnUrl,
                status,
                testMode,
                failureReason,
                failureMessage,
                sagaId,
                createdAt,
                expires);
    }

    public CheckoutSession processing(UUID saga) {
        return withStatus(CheckoutStatus.PROCESSING, null, null, saga);
    }

    public CheckoutSession completed() {
        return withStatus(CheckoutStatus.COMPLETED, null, null, sagaId);
    }

    public CheckoutSession failed(String reason, @Nullable String message) {
        if (status == CheckoutStatus.COMPLETED) {
            return this;
        }
        return withStatus(CheckoutStatus.FAILED, reason, message, sagaId);
    }

    public boolean finished() {
        return status == CheckoutStatus.COMPLETED || status == CheckoutStatus.FAILED;
    }

    private CheckoutSession withStatus(
            CheckoutStatus next, @Nullable String reason, @Nullable String message, @Nullable UUID saga) {
        return new CheckoutSession(
                id,
                userId,
                idempotencyKey,
                tier,
                provider,
                providerSessionId,
                redirectUrl,
                returnUrl,
                next,
                testMode,
                reason,
                message,
                saga,
                createdAt,
                expiresAt);
    }
}
