package com.toefly.billing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class ProviderConfigTest {

    static BillingProperties.Stripe stripe(BillingProperties.Mode mode, String key, String secret) {
        return new BillingProperties.Stripe(true, mode, key, secret, "https://api.stripe.com", false, "x");
    }

    static Object build(BillingProperties.Stripe stripe, boolean liveAllowed) {
        return ProviderConfig.stripe(
                stripe, liveAllowed, RestClient.builder(), JsonMapper.builder().build(), Clock.systemUTC());
    }

    @Test
    @DisplayName("T-F6-05: test mode refuses sk_live_ keys; live mode needs prod and live-mode=true")
    void liveKeysNeedLiveMode() {
        assertThatThrownBy(() -> build(stripe(BillingProperties.Mode.TEST, "sk_live_123", "whsec"), false))
                .hasMessageContaining("refuses live keys");
        assertThatThrownBy(() -> build(stripe(BillingProperties.Mode.LIVE, "sk_live_123", "whsec"), false))
                .hasMessageContaining("needs profile prod");
        assertThat(build(stripe(BillingProperties.Mode.LIVE, "sk_live_123", "whsec"), true))
                .isNotNull();
    }

    @Test
    @DisplayName("AC-27.08.2: an enabled provider without its keys stops the start")
    void missingKeysFailFast() {
        assertThatThrownBy(() -> build(stripe(BillingProperties.Mode.TEST, "", "whsec"), false))
                .hasMessageContaining("secret-key is required");
        assertThatThrownBy(() -> build(stripe(BillingProperties.Mode.TEST, "sk_test_1", null), false))
                .hasMessageContaining("webhook-secret is required");
    }
}
