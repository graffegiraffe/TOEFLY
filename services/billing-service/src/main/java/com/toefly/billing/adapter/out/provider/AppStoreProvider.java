package com.toefly.billing.adapter.out.provider;

import com.toefly.billing.application.port.in.InvalidSignatureException;
import com.toefly.billing.application.port.in.ProviderException;
import com.toefly.billing.application.port.out.AppStoreVerifierPort;
import com.toefly.billing.application.port.out.PaymentProvider;
import com.toefly.billing.application.port.out.ProviderEvent;
import com.toefly.billing.domain.plan.PlanTier;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * App Store: transactions of StoreKit 2 sent by the app and Server Notifications V2. Purchase, cancel and refund happen
 * in the App Store; billing only verifies and records (ADR-0021).
 */
public class AppStoreProvider implements PaymentProvider, AppStoreVerifierPort {

    private static final Logger log = LoggerFactory.getLogger(AppStoreProvider.class);

    private final AppStoreJws jws;
    private final JsonMapper json;
    private final boolean testMode;

    public AppStoreProvider(AppStoreJws jws, JsonMapper json, boolean testMode) {
        this.jws = jws;
        this.json = json;
        this.testMode = testMode;
    }

    @Override
    public PaymentProviderCode code() {
        return PaymentProviderCode.APP_STORE;
    }

    @Override
    public boolean testMode() {
        return testMode;
    }

    @Override
    public Transaction verify(String signedTransactionInfo) {
        return transaction(jws.verify(signedTransactionInfo));
    }

    static Transaction transaction(JsonNode payload) {
        String environment = payload.path("environment").asString("Sandbox");
        Integer price =
                payload.has("price") ? Math.toIntExact(payload.path("price").asLong() / 10) : null;
        return new Transaction(
                payload.path("transactionId").asString(),
                payload.path("originalTransactionId")
                        .asString(payload.path("transactionId").asString()),
                payload.path("productId").asString(),
                StripeProvider.uuid(payload.path("appAccountToken").asString(null)),
                Instant.ofEpochMilli(payload.path("purchaseDate").asLong()),
                Instant.ofEpochMilli(payload.path("expiresDate").asLong()),
                !"Production".equals(environment),
                price,
                payload.path("currency").asString(null));
    }

    @Override
    public ProviderEvent parseWebhook(Map<String, String> headers, byte[] body) {
        String signed = json.readTree(body).path("signedPayload").asString(null);
        if (signed == null) {
            throw new InvalidSignatureException("signedPayload is missing");
        }
        JsonNode notification = jws.verify(signed);
        String id = notification.path("notificationUUID").asString();
        String type = notification.path("notificationType").asString();
        String subtype = notification.path("subtype").asString("");
        String transactionJws =
                notification.path("data").path("signedTransactionInfo").asString(null);
        if (transactionJws == null) {
            return new ProviderEvent.Ignored(id, type);
        }
        Transaction transaction = verify(transactionJws);
        return switch (type) {
            case "DID_RENEW" ->
                new ProviderEvent.RenewalPaid(
                        id,
                        type,
                        transaction.originalTransactionId(),
                        transaction.transactionId(),
                        transaction.priceMinor(),
                        transaction.currency(),
                        transaction.purchaseDate(),
                        transaction.purchaseDate(),
                        transaction.expiresDate(),
                        null,
                        transaction.sandbox());
            case "DID_FAIL_TO_RENEW" ->
                new ProviderEvent.RenewalFailed(
                        id,
                        type,
                        transaction.originalTransactionId(),
                        null,
                        subtype.isEmpty() ? "DID_FAIL_TO_RENEW" : subtype,
                        "The App Store could not renew the subscription.");
            case "EXPIRED", "GRACE_PERIOD_EXPIRED" ->
                new ProviderEvent.SubscriptionEnded(id, type, transaction.originalTransactionId());
            case "REFUND", "REVOKE" -> new ProviderEvent.Refunded(id, type, transaction.transactionId());
            default -> new ProviderEvent.Ignored(id, type);
        };
    }

    @Override
    public Checkout createCheckout(CheckoutRequest request) {
        throw new ProviderException("The App Store sells only inside the iOS app.");
    }

    /** Apple refunds in the App Store; a compensated saga only logs for the admin. */
    @Override
    public void refund(String providerPaymentId, @Nullable Integer amountMinor, @Nullable String subscription) {
        log.error("ALERT App Store transaction {} needs a refund request in App Store Connect", providerPaymentId);
    }

    @Override
    public void cancelAtPeriodEnd(String providerSubscriptionId) {
        throw new ProviderException("App Store subscriptions are managed in the App Store.");
    }

    @Override
    public void resume(String providerSubscriptionId) {
        throw new ProviderException("App Store subscriptions are managed in the App Store.");
    }

    @Override
    public void changePlan(String providerSubscriptionId, PlanTier target, boolean now) {
        throw new ProviderException("App Store subscriptions are managed in the App Store.");
    }

    @Override
    public @Nullable String portalUrl(String providerCustomerId, String returnUrl) {
        return "https://apps.apple.com/account/subscriptions";
    }
}
