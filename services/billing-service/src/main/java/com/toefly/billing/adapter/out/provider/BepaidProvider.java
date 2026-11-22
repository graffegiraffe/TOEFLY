package com.toefly.billing.adapter.out.provider;

import com.toefly.billing.application.port.in.InvalidSignatureException;
import com.toefly.billing.application.port.in.ProviderException;
import com.toefly.billing.application.port.out.PaymentProvider;
import com.toefly.billing.application.port.out.ProviderEvent;
import com.toefly.billing.domain.plan.PlanTier;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * bePaid hosted payment page (CTP API) and notifications signed with the shop's RSA key in {@code Content-Signature}
 * (docs/10 §17.3). A bePaid payment buys one month; it does not renew by itself (phase 11a notes).
 */
public class BepaidProvider implements PaymentProvider {

    private final RestClient checkoutApi;
    private final RestClient gatewayApi;
    private final PublicKey notificationKey;
    private final boolean testMode;
    private final String notificationUrl;
    private final JsonMapper json;
    private final Clock clock;

    public BepaidProvider(
            RestClient checkoutApi,
            RestClient gatewayApi,
            PublicKey notificationKey,
            boolean testMode,
            String notificationUrl,
            JsonMapper json,
            Clock clock) {
        this.checkoutApi = checkoutApi;
        this.gatewayApi = gatewayApi;
        this.notificationKey = notificationKey;
        this.testMode = testMode;
        this.notificationUrl = notificationUrl;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public PaymentProviderCode code() {
        return PaymentProviderCode.BEPAID;
    }

    @Override
    public boolean testMode() {
        return testMode;
    }

    @Override
    public Checkout createCheckout(CheckoutRequest request) {
        PlanTier tier = request.tier();
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("success_url", request.successUrl());
        settings.put("decline_url", request.successUrl());
        settings.put("fail_url", request.successUrl());
        settings.put("cancel_url", request.cancelUrl());
        settings.put("notification_url", notificationUrl);
        settings.put("language", "en");
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("currency", tier.currency());
        order.put("amount", tier.priceMinor());
        order.put("description", "TOEFLY " + StripeProvider.label(tier.code()) + ", 1 month");
        order.put("tracking_id", request.checkoutSessionId().toString());
        Map<String, Object> checkout = new LinkedHashMap<>();
        checkout.put("test", testMode);
        checkout.put("transaction_type", "payment");
        checkout.put("attempts", 3);
        checkout.put("settings", settings);
        checkout.put("order", order);
        if (request.customerEmail() != null) {
            checkout.put("customer", Map.of("email", request.customerEmail()));
        }
        JsonNode response = post(checkoutApi, "/ctp/api/checkouts", Map.of("checkout", checkout));
        JsonNode body = response.path("checkout");
        String url = body.path("redirect_url").asString(null);
        if (url == null) {
            throw new ProviderException("bePaid returned no redirect_url");
        }
        return new Checkout(body.path("token").asString(), url, clock.instant().plus(Duration.ofHours(1)));
    }

    @Override
    public ProviderEvent parseWebhook(Map<String, String> headers, byte[] body) {
        verify(StripeProvider.header(headers, "content-signature"), body);
        JsonNode transaction = json.readTree(body).path("transaction");
        String uid = transaction.path("uid").asString();
        String status = transaction.path("status").asString();
        String type = "transaction." + transaction.path("type").asString("payment") + "." + status;
        String eventId = uid + ":" + status;
        boolean test = transaction.path("test").asBoolean(true);
        if ("refund".equals(transaction.path("type").asString()) && "successful".equals(status)) {
            return new ProviderEvent.Refunded(
                    eventId, type, transaction.path("parent_uid").asString());
        }
        if (!"payment".equals(transaction.path("type").asString("payment"))) {
            return new ProviderEvent.Ignored(eventId, type);
        }
        return switch (status) {
            case "successful" ->
                new ProviderEvent.CheckoutCompleted(
                        eventId,
                        type,
                        StripeProvider.uuid(transaction.path("tracking_id").asString(null)),
                        null,
                        null,
                        null,
                        uid,
                        null,
                        null,
                        transaction.has("amount") ? transaction.path("amount").asInt() : null,
                        StripeProvider.currency(transaction.path("currency").asString(null)),
                        time(transaction
                                .path("paid_at")
                                .asString(transaction.path("created_at").asString(null))),
                        null,
                        null,
                        transaction.path("receipt_url").asString(null),
                        test);
            case "failed", "expired" ->
                new ProviderEvent.CheckoutFailed(
                        eventId,
                        type,
                        StripeProvider.uuid(transaction.path("tracking_id").asString(null)),
                        null,
                        uid,
                        status,
                        transaction.path("message").asString("The payment did not go through."));
            default -> new ProviderEvent.Ignored(eventId, type);
        };
    }

    @Override
    public void refund(String providerPaymentId, @Nullable Integer amountMinor, @Nullable String subscription) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("parent_uid", providerPaymentId);
        if (amountMinor != null) {
            request.put("amount", amountMinor);
        }
        request.put("reason", "TOEFLY could not activate the plan");
        post(gatewayApi, "/transactions/refunds", Map.of("request", request));
    }

    /** A bePaid month does not renew; the cancel is local. */
    @Override
    public void cancelAtPeriodEnd(String providerSubscriptionId) {}

    @Override
    public void resume(String providerSubscriptionId) {}

    /** AC-27.05.3 for bePaid (the difference for the rest of the period) needs recurring tokens; not in phase 11a. */
    @Override
    public void changePlan(String providerSubscriptionId, PlanTier target, boolean now) {}

    @Override
    public @Nullable String portalUrl(String providerCustomerId, String returnUrl) {
        return null;
    }

    void verify(@Nullable String signature, byte[] body) {
        if (signature == null || signature.isBlank()) {
            throw new InvalidSignatureException("Content-Signature is missing");
        }
        try {
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initVerify(notificationKey);
            rsa.update(body);
            if (!rsa.verify(Base64.getDecoder().decode(signature.trim()))) {
                throw new InvalidSignatureException("Content-Signature does not match");
            }
        } catch (GeneralSecurityException | IllegalArgumentException malformed) {
            throw new InvalidSignatureException("Content-Signature is malformed");
        }
    }

    private Instant time(@Nullable String value) {
        if (value == null) {
            return clock.instant();
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException other) {
            return clock.instant();
        }
    }

    private JsonNode post(RestClient client, String path, Object body) {
        try {
            String response = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("X-API-Version", "2")
                    .body(json.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            return json.readTree(response == null ? "{}" : response);
        } catch (RestClientException failure) {
            throw new ProviderException("bePaid " + path + " failed: " + failure.getMessage(), failure);
        }
    }
}
