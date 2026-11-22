package com.toefly.billing.adapter.out.provider;

import com.toefly.billing.application.port.in.InvalidSignatureException;
import com.toefly.billing.application.port.in.ProviderException;
import com.toefly.billing.application.port.out.PaymentProvider;
import com.toefly.billing.application.port.out.ProviderEvent;
import com.toefly.billing.domain.plan.PlanTier;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stripe Checkout and Billing over the REST API (form-encoded, no SDK). Webhooks: {@code Stripe-Signature} =
 * {@code t=…,v1=HMAC-SHA256(secret, t + "." + body)}, verified on the raw body with a 300 s tolerance (docs/10 §17.3).
 * In emulator mode (local profile without Stripe keys) checkout opens the local test page of billing-service instead
 * of calling Stripe; webhooks are still signed and verified the same way.
 */
public class StripeProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(StripeProvider.class);
    static final Duration TOLERANCE = Duration.ofSeconds(300);

    private final RestClient api;
    private final String webhookSecret;
    private final boolean testMode;
    private final @Nullable String emulatorUrl;
    private final JsonMapper json;
    private final Clock clock;

    /**
     * @param emulatorUrl base of the local checkout page, or null to call Stripe
     */
    public StripeProvider(
            RestClient api,
            String webhookSecret,
            boolean testMode,
            @Nullable String emulatorUrl,
            JsonMapper json,
            Clock clock) {
        this.api = api;
        this.webhookSecret = webhookSecret;
        this.testMode = testMode;
        this.emulatorUrl = emulatorUrl;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public PaymentProviderCode code() {
        return PaymentProviderCode.STRIPE;
    }

    @Override
    public boolean testMode() {
        return testMode;
    }

    @Override
    public Checkout createCheckout(CheckoutRequest request) {
        Instant expires = clock.instant().plus(Duration.ofMinutes(60));
        if (emulatorUrl != null) {
            String session =
                    "cs_test_emulated_" + request.checkoutSessionId().toString().replace("-", "");
            return new Checkout(session, emulatorUrl + "/dev/checkout/" + request.checkoutSessionId(), expires);
        }
        PlanTier tier = request.tier();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("client_reference_id", request.userId().toString());
        form.add("success_url", request.successUrl());
        form.add("cancel_url", request.cancelUrl());
        form.add("expires_at", Long.toString(expires.getEpochSecond()));
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", tier.currency().toLowerCase(Locale.ROOT));
        form.add("line_items[0][price_data][unit_amount]", Integer.toString(tier.priceMinor()));
        form.add("line_items[0][price_data][recurring][interval]", "month");
        form.add("line_items[0][price_data][product_data][name]", "TOEFLY " + label(tier.code()));
        metadata(form, "metadata", request);
        metadata(form, "subscription_data[metadata]", request);
        if (request.customerEmail() != null) {
            form.add("customer_email", request.customerEmail());
        }
        JsonNode session =
                post("/v1/checkout/sessions", form, request.checkoutSessionId().toString());
        String url = session.path("url").asString();
        URI redirect = URI.create(url);
        if (!"checkout.stripe.com".equals(redirect.getHost())) {
            throw new ProviderException("Stripe returned an unexpected checkout host " + redirect.getHost());
        }
        return new Checkout(
                session.path("id").asString(),
                url,
                Instant.ofEpochSecond(session.path("expires_at").asLong(expires.getEpochSecond())));
    }

    @Override
    public ProviderEvent parseWebhook(Map<String, String> headers, byte[] body) {
        verify(header(headers, "stripe-signature"), body);
        JsonNode event = json.readTree(body);
        String id = event.path("id").asString();
        String type = event.path("type").asString();
        JsonNode object = event.path("data").path("object");
        Instant created = Instant.ofEpochSecond(
                event.path("created").asLong(clock.instant().getEpochSecond()));
        boolean test = !event.path("livemode").asBoolean(false);
        return switch (type) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" ->
                "paid".equals(object.path("payment_status").asString())
                        ? checkoutCompleted(id, type, object, created, test)
                        : new ProviderEvent.Ignored(id, type);
            case "checkout.session.async_payment_failed", "checkout.session.expired" ->
                new ProviderEvent.CheckoutFailed(
                        id,
                        type,
                        uuid(object.path("metadata").path("checkoutSessionId").asString(null)),
                        object.path("id").asString(null),
                        null,
                        type.endsWith("expired") ? "expired" : "async_payment_failed",
                        type.endsWith("expired") ? "The payment page expired." : "The payment did not go through.");
            case "payment_intent.payment_failed" -> {
                JsonNode error = object.path("last_payment_error");
                yield new ProviderEvent.CheckoutFailed(
                        id,
                        type,
                        uuid(object.path("metadata").path("checkoutSessionId").asString(null)),
                        null,
                        object.path("id").asString(null),
                        error.path("decline_code").asString(error.path("code").asString("payment_failed")),
                        error.path("message").asString("The payment did not go through."));
            }
            case "invoice.paid" -> renewal(id, type, object, created, test);
            case "invoice.payment_failed" ->
                "subscription_create".equals(object.path("billing_reason").asString())
                        ? new ProviderEvent.Ignored(id, type)
                        : new ProviderEvent.RenewalFailed(
                                id,
                                type,
                                subscriptionOf(object),
                                object.path("id").asString(null),
                                "payment_failed",
                                "Your card was declined. Update your payment method.");
            case "customer.subscription.deleted" ->
                new ProviderEvent.SubscriptionEnded(id, type, object.path("id").asString());
            case "charge.refunded", "charge.dispute.created" -> {
                String invoice = object.path("invoice").asString(null);
                String ref = invoice != null
                        ? invoice
                        : object.path("payment_intent")
                                .asString(object.path("id").asString());
                yield new ProviderEvent.Refunded(id, type, ref);
            }
            default -> new ProviderEvent.Ignored(id, type);
        };
    }

    private ProviderEvent checkoutCompleted(String id, String type, JsonNode session, Instant created, boolean test) {
        JsonNode metadata = session.path("metadata");
        String invoice = session.path("invoice").asString(null);
        String paymentIntent = session.path("payment_intent").asString(null);
        String paymentRef = invoice != null
                ? invoice
                : paymentIntent != null ? paymentIntent : session.path("id").asString();
        String tier = metadata.path("planTierCode").asString(null);
        return new ProviderEvent.CheckoutCompleted(
                id,
                type,
                uuid(metadata.path("checkoutSessionId").asString(null)),
                session.path("id").asString(null),
                uuid(session.path("client_reference_id").asString(null)),
                tier == null ? null : PlanTierCode.valueOf(tier),
                paymentRef,
                session.path("subscription").asString(null),
                session.path("customer").asString(null),
                session.has("amount_total") ? session.path("amount_total").asInt() : null,
                currency(session.path("currency").asString(null)),
                created,
                null,
                null,
                null,
                test);
    }

    private ProviderEvent renewal(String id, String type, JsonNode invoice, Instant created, boolean test) {
        if ("subscription_create".equals(invoice.path("billing_reason").asString())) {
            return new ProviderEvent.Ignored(id, type); // the first invoice is the checkout payment
        }
        JsonNode period = invoice.path("lines").path("data").path(0).path("period");
        Instant start =
                period.has("start") ? Instant.ofEpochSecond(period.path("start").asLong()) : created;
        Instant end =
                period.has("end") ? Instant.ofEpochSecond(period.path("end").asLong()) : PlanTier.periodEnd(start);
        return new ProviderEvent.RenewalPaid(
                id,
                type,
                subscriptionOf(invoice),
                invoice.path("id").asString(),
                invoice.has("amount_paid") ? invoice.path("amount_paid").asInt() : null,
                currency(invoice.path("currency").asString(null)),
                created,
                start,
                end,
                invoice.path("hosted_invoice_url").asString(null),
                test);
    }

    /** The subscription of an invoice: top-level in older API versions, under parent in newer ones. */
    private static String subscriptionOf(JsonNode invoice) {
        String direct = invoice.path("subscription").asString(null);
        if (direct != null) {
            return direct;
        }
        return invoice.path("parent")
                .path("subscription_details")
                .path("subscription")
                .asString("");
    }

    @Override
    public void refund(String providerPaymentId, @Nullable Integer amountMinor, @Nullable String subscription) {
        if (emulatorUrl != null) {
            log.info("Stripe emulator: refund of {} recorded", providerPaymentId);
            return;
        }
        String key = "refund:" + providerPaymentId;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (providerPaymentId.startsWith("in_")) {
            JsonNode invoice = get("/v1/invoices/" + providerPaymentId);
            String intent = invoice.path("payment_intent").asString(null);
            String charge = invoice.path("charge").asString(null);
            if (intent == null && charge == null) {
                intent = invoice.path("payments")
                        .path("data")
                        .path(0)
                        .path("payment")
                        .path("payment_intent")
                        .asString(null);
            }
            if (intent != null) {
                form.add("payment_intent", intent);
            } else if (charge != null) {
                form.add("charge", charge);
            } else {
                throw new ProviderException("Invoice " + providerPaymentId + " has no payment to refund");
            }
        } else if (providerPaymentId.startsWith("ch_")) {
            form.add("charge", providerPaymentId);
        } else {
            form.add("payment_intent", providerPaymentId);
        }
        post("/v1/refunds", form, key);
        if (subscription != null) {
            delete("/v1/subscriptions/" + subscription);
        }
    }

    @Override
    public void cancelAtPeriodEnd(String providerSubscriptionId) {
        updateSubscription(providerSubscriptionId, Map.of("cancel_at_period_end", "true"));
    }

    @Override
    public void resume(String providerSubscriptionId) {
        updateSubscription(providerSubscriptionId, Map.of("cancel_at_period_end", "false"));
    }

    /** The new price applies now with proration (upgrade) or from the next invoice without it (downgrade). */
    @Override
    public void changePlan(String providerSubscriptionId, PlanTier target, boolean now) {
        if (emulatorUrl != null) {
            log.info("Stripe emulator: {} → {}", providerSubscriptionId, target.code());
            return;
        }
        JsonNode subscription = get("/v1/subscriptions/" + providerSubscriptionId);
        JsonNode item = subscription.path("items").path("data").path(0);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("items[0][id]", item.path("id").asString());
        form.add("items[0][price_data][currency]", target.currency().toLowerCase(Locale.ROOT));
        form.add("items[0][price_data][unit_amount]", Integer.toString(target.priceMinor()));
        form.add("items[0][price_data][recurring][interval]", "month");
        form.add(
                "items[0][price_data][product]",
                item.path("price").path("product").asString());
        form.add("proration_behavior", now ? "always_invoice" : "none");
        form.add("metadata[planTierCode]", target.code().name());
        post(
                "/v1/subscriptions/" + providerSubscriptionId,
                form,
                "change:" + providerSubscriptionId + ":" + target.code());
    }

    @Override
    public @Nullable String portalUrl(String providerCustomerId, String returnUrl) {
        if (emulatorUrl != null) {
            return returnUrl;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("customer", providerCustomerId);
        form.add("return_url", returnUrl);
        return post("/v1/billing_portal/sessions", form, null).path("url").asString();
    }

    /** Signs a body like Stripe; used by the local emulator and the tests. */
    public String sign(byte[] body, Instant at) {
        long t = at.getEpochSecond();
        return "t=" + t + ",v1=" + hmac(t + "." + new String(body, StandardCharsets.UTF_8));
    }

    void verify(@Nullable String header, byte[] body) {
        if (header == null || header.isBlank()) {
            throw new InvalidSignatureException("Stripe-Signature is missing");
        }
        Long timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if (pair[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(pair[1]);
                } catch (NumberFormatException malformed) {
                    throw new InvalidSignatureException("Stripe-Signature has a malformed timestamp");
                }
            } else if (pair[0].equals("v1")) {
                signatures.add(pair[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            throw new InvalidSignatureException("Stripe-Signature has no timestamp or v1 signature");
        }
        long age = Math.abs(clock.instant().getEpochSecond() - timestamp);
        if (age > TOLERANCE.getSeconds()) {
            throw new InvalidSignatureException("Stripe-Signature is outside the tolerance");
        }
        byte[] expected =
                hmac(timestamp + "." + new String(body, StandardCharsets.UTF_8)).getBytes(StandardCharsets.US_ASCII);
        boolean match = signatures.stream()
                .anyMatch(signature -> MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII)));
        if (!match) {
            throw new InvalidSignatureException("Stripe-Signature does not match");
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void updateSubscription(String id, Map<String, String> fields) {
        if (emulatorUrl != null) {
            log.info("Stripe emulator: subscription {} updated with {}", id, fields);
            return;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        fields.forEach(form::add);
        post("/v1/subscriptions/" + id, form, null);
    }

    private JsonNode post(String path, MultiValueMap<String, String> form, @Nullable String idempotencyKey) {
        try {
            String body = api.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(headers -> {
                        if (idempotencyKey != null) {
                            headers.set("Idempotency-Key", idempotencyKey);
                        }
                    })
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return json.readTree(body == null ? "{}" : body);
        } catch (RestClientException failure) {
            throw new ProviderException("Stripe " + path + " failed: " + failure.getMessage(), failure);
        }
    }

    private JsonNode get(String path) {
        try {
            String body = api.get().uri(path).retrieve().body(String.class);
            return json.readTree(body == null ? "{}" : body);
        } catch (RestClientException failure) {
            throw new ProviderException("Stripe " + path + " failed: " + failure.getMessage(), failure);
        }
    }

    private void delete(String path) {
        try {
            api.delete().uri(path).retrieve().toBodilessEntity();
        } catch (RestClientException failure) {
            throw new ProviderException("Stripe " + path + " failed: " + failure.getMessage(), failure);
        }
    }

    private static void metadata(MultiValueMap<String, String> form, String prefix, CheckoutRequest request) {
        form.add(prefix + "[checkoutSessionId]", request.checkoutSessionId().toString());
        form.add(prefix + "[planTierCode]", request.tier().code().name());
        form.add(prefix + "[userId]", request.userId().toString());
    }

    static String label(PlanTierCode code) {
        return switch (code) {
            case STUDENT_FREE -> "Student Free";
            case STUDENT_PRO -> "Student Pro";
            case TUTOR -> "Tutor";
            case TUTOR_PLUS -> "Tutor Plus";
        };
    }

    static @Nullable String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    static @Nullable UUID uuid(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notUuid) {
            return null;
        }
    }

    static @Nullable String currency(@Nullable String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
