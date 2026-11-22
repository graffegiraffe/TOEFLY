package com.toefly.billing.application.port.out;

import com.toefly.billing.application.port.in.InvalidSignatureException;
import com.toefly.billing.domain.plan.PlanTier;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface PaymentProvider {

    PaymentProviderCode code();

    /** Test mode shows the badge "Test mode — no real charges" (AC-27.01.3). */
    boolean testMode();

    /** Creates a hosted payment page; card data never passes TOEFLY (AC-27.03.1). */
    Checkout createCheckout(CheckoutRequest request);

    /**
     * Verifies the signature on the raw body before parsing (T-F6-01).
     *
     * @throws InvalidSignatureException for a missing or wrong signature
     */
    ProviderEvent parseWebhook(Map<String, String> headers, byte[] body);

    /** Compensation of RecordPayment: returns the money (AC-27.03.3). */
    void refund(String providerPaymentId, @Nullable Integer amountMinor, @Nullable String providerSubscriptionId);

    /** AC-27.05.2. */
    void cancelAtPeriodEnd(String providerSubscriptionId);

    void resume(String providerSubscriptionId);

    /** AC-27.05.3: change the price of the subscription; {@code now} — with proration of the provider. */
    void changePlan(String providerSubscriptionId, PlanTier target, boolean now);

    /** A page of the provider to change the card or cancel; null when the provider has none. */
    @Nullable
    String portalUrl(String providerCustomerId, String returnUrl);

    record CheckoutRequest(
            UUID checkoutSessionId,
            UUID userId,
            PlanTier tier,
            String successUrl,
            String cancelUrl,
            @Nullable String customerEmail) {}

    record Checkout(String providerSessionId, String redirectUrl, Instant expiresAt) {}
}
