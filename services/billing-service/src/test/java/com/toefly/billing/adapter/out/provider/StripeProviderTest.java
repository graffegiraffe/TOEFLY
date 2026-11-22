package com.toefly.billing.adapter.out.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.billing.application.port.in.InvalidSignatureException;
import com.toefly.billing.application.port.out.ProviderEvent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class StripeProviderTest {

    static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    final StripeProvider stripe = new StripeProvider(
            RestClient.create(),
            "whsec_unit",
            true,
            null,
            JsonMapper.builder().build(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    static byte[] body(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("T-F6-01, docs/10 §17.3: signature on the raw body with a 300 s tolerance")
    void signature() {
        byte[] event = body("{\"id\":\"evt_1\",\"type\":\"customer.created\",\"data\":{\"object\":{}}}");

        assertThat(stripe.parseWebhook(Map.of("stripe-signature", stripe.sign(event, NOW)), event))
                .isInstanceOf(ProviderEvent.Ignored.class);
        assertThatThrownBy(() -> stripe.parseWebhook(Map.of(), event)).isInstanceOf(InvalidSignatureException.class);
        assertThatThrownBy(() -> stripe.parseWebhook(
                        Map.of("stripe-signature", stripe.sign(event, NOW.minus(Duration.ofSeconds(301)))), event))
                .isInstanceOf(InvalidSignatureException.class);
        byte[] tampered = body("{\"id\":\"evt_1\",\"type\":\"invoice.paid\",\"data\":{\"object\":{}}}");
        assertThatThrownBy(() -> stripe.parseWebhook(Map.of("stripe-signature", stripe.sign(event, NOW)), tampered))
                .isInstanceOf(InvalidSignatureException.class);
    }

    @Test
    @DisplayName("Stripe events map to provider-neutral events (renewal, first invoice, failure, end)")
    void mapping() {
        byte[] renewal = body("""
                {"id":"evt_r","type":"invoice.paid","created":1764000000,"livemode":false,
                 "data":{"object":{"id":"in_2","billing_reason":"subscription_cycle",
                 "parent":{"subscription_details":{"subscription":"sub_1"}},"amount_paid":1200,"currency":"eur",
                 "lines":{"data":[{"period":{"start":1764000000,"end":1766592000}}]}}}}
                """);
        ProviderEvent event = stripe.parseWebhook(Map.of("stripe-signature", stripe.sign(renewal, NOW)), renewal);
        assertThat(event).isInstanceOfSatisfying(ProviderEvent.RenewalPaid.class, paid -> {
            assertThat(paid.providerSubscriptionId()).isEqualTo("sub_1");
            assertThat(paid.currency()).isEqualTo("EUR");
            assertThat(paid.periodEnd()).isEqualTo(Instant.ofEpochSecond(1766592000));
        });

        byte[] first = body("""
                {"id":"evt_f","type":"invoice.paid","data":{"object":{"id":"in_1","billing_reason":"subscription_create"}}}
                """);
        assertThat(stripe.parseWebhook(Map.of("stripe-signature", stripe.sign(first, NOW)), first))
                .isInstanceOf(ProviderEvent.Ignored.class);

        byte[] ended = body("""
                {"id":"evt_e","type":"customer.subscription.deleted","data":{"object":{"id":"sub_1"}}}
                """);
        assertThat(stripe.parseWebhook(Map.of("stripe-signature", stripe.sign(ended, NOW)), ended))
                .isEqualTo(new ProviderEvent.SubscriptionEnded("evt_e", "customer.subscription.deleted", "sub_1"));
    }
}
