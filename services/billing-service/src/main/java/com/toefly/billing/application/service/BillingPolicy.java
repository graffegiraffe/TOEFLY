package com.toefly.billing.application.service;

import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.saga.RetryPolicy;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Rules of billing from the configuration {@code toefly.billing.*} (docs/02 EP-27).
 *
 * @param returnUrls allowed return address prefixes of the web (T-F6-03), such as {@code https://app.toefly.app/billing}
 * @param webProviders providers offered on the web, in the order of the buttons (AC-27.08.3)
 * @param appStoreProducts StoreKit product ids and their tiers
 * @param identityPepper secret of the HMAC of identities in the trial registry (AC-27.02.4)
 */
public record BillingPolicy(
        Duration trialLength,
        Duration trialEndingNotice,
        Duration grace,
        Duration trialReuseWindow,
        Duration checkoutLifetime,
        RetryPolicy sagaRetry,
        List<String> returnUrls,
        List<PaymentProviderCode> webProviders,
        Map<String, PlanTierCode> appStoreProducts,
        String identityPepper) {

    public BillingPolicy {
        returnUrls = List.copyOf(returnUrls);
        webProviders = List.copyOf(webProviders);
        appStoreProducts = Map.copyOf(appStoreProducts);
    }

    public static BillingPolicy defaults() {
        return new BillingPolicy(
                Duration.ofDays(7),
                Duration.ofHours(48),
                Duration.ofDays(7),
                Duration.ofDays(365),
                Duration.ofHours(1),
                RetryPolicy.standard(),
                List.of("http://app.toefly.localhost:5173/billing"),
                List.of(PaymentProviderCode.STRIPE, PaymentProviderCode.BEPAID),
                Map.of("app.toefly.pro.monthly", PlanTierCode.STUDENT_PRO),
                "local-pepper");
    }

    public boolean allowedReturnUrl(String url) {
        return returnUrls.stream()
                .anyMatch(allowed -> !url.contains("..")
                        && !url.contains("@")
                        && (url.equals(allowed) || url.startsWith(allowed + "/") || url.startsWith(allowed + "?")));
    }
}
