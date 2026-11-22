package com.toefly.billing.adapter.out.provider;

import com.toefly.billing.application.port.out.CheckoutEmulatorPort;
import com.toefly.billing.domain.checkout.CheckoutSession;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the Stripe events of test cards: 4242 4242 4242 4242 pays ({@code checkout.session.completed}), 4000 0000 0000
 * 0002 is declined ({@code payment_intent.payment_failed}, {@code card_declined}). Only the local profile has it.
 */
public class StripeEmulatorAdapter implements CheckoutEmulatorPort {

    private final StripeProvider signer;
    private final JsonMapper json;
    private final Clock clock;

    public StripeEmulatorAdapter(StripeProvider signer, JsonMapper json, Clock clock) {
        this.signer = signer;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public Delivery notification(CheckoutSession session, boolean approve) {
        Instant now = clock.instant();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, Object> metadata = Map.of(
                "checkoutSessionId", session.id().toString(),
                "planTierCode", session.tier().name(),
                "userId", session.userId().toString());
        Map<String, Object> object = new LinkedHashMap<>();
        String type;
        if (approve) {
            type = "checkout.session.completed";
            object.put("id", session.providerSessionId());
            object.put("object", "checkout.session");
            object.put("client_reference_id", session.userId().toString());
            object.put("metadata", metadata);
            object.put("subscription", "sub_emulated_" + suffix);
            object.put("customer", "cus_emulated_" + suffix);
            object.put("invoice", "in_emulated_" + suffix);
            object.put("payment_status", "paid");
            object.put("status", "complete");
            object.put("currency", "eur");
            object.put("amount_total", amount(session));
        } else {
            type = "payment_intent.payment_failed";
            object.put("id", "pi_emulated_" + suffix);
            object.put("object", "payment_intent");
            object.put("metadata", metadata);
            object.put(
                    "last_payment_error",
                    Map.of(
                            "code",
                            "card_declined",
                            "decline_code",
                            "generic_decline",
                            "message",
                            "Your card was declined."));
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "evt_emulated_" + suffix);
        event.put("object", "event");
        event.put("type", type);
        event.put("created", now.getEpochSecond());
        event.put("livemode", false);
        event.put("data", Map.of("object", object));
        byte[] body = json.writeValueAsString(event).getBytes(StandardCharsets.UTF_8);
        return new Delivery(Map.of("Stripe-Signature", signer.sign(body, now)), body);
    }

    private static int amount(CheckoutSession session) {
        return switch (session.tier()) {
            case STUDENT_PRO -> 1200;
            case TUTOR -> 2900;
            case TUTOR_PLUS -> 5900;
            case STUDENT_FREE -> 0;
        };
    }
}
