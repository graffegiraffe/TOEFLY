package com.toefly.billing.application.service;

import com.toefly.billing.application.port.in.BillingException;
import com.toefly.billing.application.port.out.PaymentProvider;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The enabled provider adapters by code; a disabled provider has no adapter bean (AC-27.08.3). */
public class PaymentProviders {

    private final Map<PaymentProviderCode, PaymentProvider> byCode = new EnumMap<>(PaymentProviderCode.class);

    public PaymentProviders(List<PaymentProvider> providers) {
        providers.forEach(provider -> byCode.put(provider.code(), provider));
    }

    public Optional<PaymentProvider> find(PaymentProviderCode code) {
        return Optional.ofNullable(byCode.get(code));
    }

    public PaymentProvider require(PaymentProviderCode code) {
        return find(code)
                .orElseThrow(() -> BillingException.unprocessable(
                        "provider-disabled", "Payment provider " + code + " is not enabled."));
    }
}
