package com.toefly.billing.config;

import com.toefly.billing.adapter.out.provider.AppStoreJws;
import com.toefly.billing.adapter.out.provider.AppStoreProvider;
import com.toefly.billing.adapter.out.provider.BepaidProvider;
import com.toefly.billing.adapter.out.provider.StripeProvider;
import com.toefly.billing.application.port.out.AppStoreVerifierPort;
import com.toefly.billing.application.port.out.PaymentProvider;
import com.toefly.billing.application.service.PaymentProviders;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Provider adapters by configuration (US-27.08): a disabled provider has no bean and is not offered; live mode needs
 * profile {@code prod} and {@code toefly.billing.live-mode=true}, test mode refuses live keys (T-F6-05); missing keys
 * stop the start (AC-27.08.2).
 */
@Configuration(proxyBeanMethods = false)
class ProviderConfig {

    @Bean
    PaymentProviders paymentProviders(
            BillingProperties properties,
            Environment environment,
            RestClient.Builder builder,
            JsonMapper json,
            Clock clock) {
        boolean liveAllowed = properties.liveMode()
                && Arrays.asList(environment.getActiveProfiles()).contains("prod");
        List<PaymentProvider> providers = new ArrayList<>();
        BillingProperties.Stripe stripe = properties.providers().stripe();
        if (stripe.enabled()) {
            providers.add(stripe(stripe, liveAllowed, builder, json, clock));
        }
        BillingProperties.Bepaid bepaid = properties.providers().bepaid();
        if (bepaid.enabled()) {
            providers.add(bepaid(bepaid, liveAllowed, builder, json, clock));
        }
        AppStoreProvider appStore = appStore(properties.providers().appStore(), liveAllowed, json, clock);
        if (appStore != null) {
            providers.add(appStore);
        }
        return new PaymentProviders(providers);
    }

    /** The local checkout page signs its webhooks like Stripe (profile local without Stripe keys). */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            value = "toefly.billing.providers.stripe.emulator",
            havingValue = "true")
    com.toefly.billing.application.port.out.CheckoutEmulatorPort stripeEmulator(
            BillingProperties properties, RestClient.Builder builder, JsonMapper json, Clock clock) {
        StripeProvider signer = stripe(properties.providers().stripe(), false, builder, json, clock);
        return new com.toefly.billing.adapter.out.provider.StripeEmulatorAdapter(signer, json, clock);
    }

    /** StoreKit transactions need the App Store adapter; without it every transaction is refused. */
    @Bean
    AppStoreVerifierPort appStoreVerifier(PaymentProviders providers) {
        return signed -> providers
                .find(com.toefly.billing.domain.subscription.PaymentProviderCode.APP_STORE)
                .map(provider -> ((AppStoreVerifierPort) provider).verify(signed))
                .orElseThrow(() -> new com.toefly.billing.application.port.in.InvalidSignatureException(
                        "The App Store is not enabled."));
    }

    static StripeProvider stripe(
            BillingProperties.Stripe stripe,
            boolean liveAllowed,
            RestClient.Builder builder,
            JsonMapper json,
            Clock clock) {
        boolean live = mode(stripe.mode(), liveAllowed, "stripe");
        String webhookSecret = require(stripe.webhookSecret(), "toefly.billing.providers.stripe.webhook-secret");
        if (stripe.emulator()) {
            if (live) {
                throw new IllegalStateException("The Stripe emulator runs only in test mode.");
            }
            return new StripeProvider(builder.build(), webhookSecret, true, stripe.emulatorUrl(), json, clock);
        }
        String key = require(stripe.secretKey(), "toefly.billing.providers.stripe.secret-key");
        if (key.startsWith("sk_live_") != live) {
            throw new IllegalStateException(
                    live ? "Stripe live mode needs an sk_live_ key." : "Stripe test mode refuses live keys (T-F6-05).");
        }
        RestClient api = builder.clone()
                .baseUrl(stripe.apiUrl())
                .requestFactory(timeouts(Duration.ofSeconds(2), Duration.ofSeconds(10)))
                .defaultHeaders(headers -> headers.setBasicAuth(key, ""))
                .build();
        return new StripeProvider(api, webhookSecret, !live, null, json, clock);
    }

    static BepaidProvider bepaid(
            BillingProperties.Bepaid bepaid,
            boolean liveAllowed,
            RestClient.Builder builder,
            JsonMapper json,
            Clock clock) {
        boolean live = mode(bepaid.mode(), liveAllowed, "bepaid");
        String shop = require(bepaid.shopId(), "toefly.billing.providers.bepaid.shop-id");
        String secret = require(bepaid.secretKey(), "toefly.billing.providers.bepaid.secret-key");
        PublicKey key = rsa(require(bepaid.publicKey(), "toefly.billing.providers.bepaid.public-key"));
        RestClient.Builder authorized = builder.clone()
                .requestFactory(timeouts(Duration.ofSeconds(2), Duration.ofSeconds(10)))
                .defaultHeaders(headers -> headers.setBasicAuth(shop, secret));
        return new BepaidProvider(
                authorized.clone().baseUrl(bepaid.checkoutUrl()).build(),
                authorized.clone().baseUrl(bepaid.gatewayUrl()).build(),
                key,
                !live,
                bepaid.notificationUrl(),
                json,
                clock);
    }

    static @Nullable AppStoreProvider appStore(
            BillingProperties.AppStore appStore, boolean liveAllowed, JsonMapper json, Clock clock) {
        if (!appStore.enabled()) {
            return null;
        }
        boolean live = mode(appStore.mode(), liveAllowed, "app-store");
        X509Certificate root =
                certificate(require(appStore.rootCertificate(), "toefly.billing.providers.app-store.root-certificate"));
        return new AppStoreProvider(new AppStoreJws(root, json, clock), json, !live);
    }

    private static boolean mode(BillingProperties.Mode mode, boolean liveAllowed, String provider) {
        if (mode == BillingProperties.Mode.LIVE && !liveAllowed) {
            throw new IllegalStateException(
                    "Live mode of " + provider + " needs profile prod and toefly.billing.live-mode=true (T-F6-05).");
        }
        return mode == BillingProperties.Mode.LIVE;
    }

    private static String require(@Nullable String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " is required for an enabled provider (AC-27.08.2).");
        }
        return value;
    }

    private static SimpleClientHttpRequestFactory timeouts(Duration connect, Duration read) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return factory;
    }

    /** A public key as PEM, base64 DER, or a path to a PEM file. */
    static PublicKey rsa(String value) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der(value)));
        } catch (GeneralSecurityException invalid) {
            throw new IllegalStateException("bePaid public key is invalid", invalid);
        }
    }

    /** A certificate as PEM, base64 DER, or a path to a PEM/DER file. */
    static X509Certificate certificate(String value) {
        try {
            return (X509Certificate)
                    CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der(value)));
        } catch (GeneralSecurityException invalid) {
            throw new IllegalStateException("App Store root certificate is invalid", invalid);
        }
    }

    private static byte[] der(String value) {
        String text = value.strip();
        try {
            Path path = Path.of(text);
            if (!text.startsWith("-----") && text.length() < 1024 && Files.isRegularFile(path)) {
                byte[] bytes = Files.readAllBytes(path);
                String content = new String(bytes, StandardCharsets.US_ASCII);
                if (!content.contains("-----BEGIN")) {
                    return bytes;
                }
                text = content;
            }
        } catch (java.io.IOException | java.nio.file.InvalidPathException notAFile) {
            // inline value
        }
        String base64 = text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
