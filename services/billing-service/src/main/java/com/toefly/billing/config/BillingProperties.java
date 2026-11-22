package com.toefly.billing.config;

import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code toefly.billing.*}: provider modes and keys, policy durations, jobs (docs/02 US-27.08, docs/10 §17.4). */
@ConfigurationProperties("toefly.billing")
public record BillingProperties(
        @DefaultValue("false") boolean liveMode,
        @DefaultValue("7d") Duration trialLength,
        @DefaultValue("48h") Duration trialEndingNotice,
        @DefaultValue("7d") Duration grace,
        @DefaultValue("365d") Duration trialReuseWindow,
        @DefaultValue("1h") Duration checkoutLifetime,

        @DefaultValue("http://app.toefly.localhost:5173/billing")
        List<String> returnUrls,

        @DefaultValue({"STRIPE", "BEPAID"}) List<PaymentProviderCode> webProviders,
        @DefaultValue("local-pepper") String identityPepper,
        Map<String, PlanTierCode> appStoreProducts,
        @DefaultValue Saga saga,
        @DefaultValue Jobs jobs,
        @DefaultValue Classroom classroom,
        @DefaultValue Providers providers) {

    public record Saga(
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("5s") Duration firstDelay,
            @DefaultValue("1h") Duration maxDelay) {}

    public record Jobs(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("2s") Duration sagaInterval,
            @DefaultValue("15s") Duration webhookInterval,
            @DefaultValue("60s") Duration timerInterval) {}

    public record Classroom(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("http://localhost:8091") String url) {}

    public record Providers(
            @DefaultValue Stripe stripe,
            @DefaultValue Bepaid bepaid,
            @DefaultValue AppStore appStore) {}

    public enum Mode {
        TEST,
        LIVE
    }

    public record Stripe(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("TEST") Mode mode,
            @Nullable String secretKey,
            @Nullable String webhookSecret,
            @DefaultValue("https://api.stripe.com") String apiUrl,
            @DefaultValue("false") boolean emulator,
            @DefaultValue("http://localhost:8092") String emulatorUrl) {}

    public record Bepaid(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("TEST") Mode mode,
            @Nullable String shopId,
            @Nullable String secretKey,
            @Nullable String publicKey,
            @DefaultValue("https://checkout.bepaid.by") String checkoutUrl,
            @DefaultValue("https://gateway.bepaid.by") String gatewayUrl,

            @DefaultValue("http://localhost:8080/api/v1/billing/webhooks/bepaid")
            String notificationUrl) {}

    public record AppStore(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("TEST") Mode mode,
            @Nullable String rootCertificate) {}
}
