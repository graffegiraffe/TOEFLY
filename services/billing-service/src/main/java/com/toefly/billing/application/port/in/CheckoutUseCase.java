package com.toefly.billing.application.port.in;

import com.toefly.billing.domain.checkout.CheckoutSession;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.util.UUID;

public interface CheckoutUseCase {

    Result create(Caller caller, PlanTierCode tier, PaymentProviderCode provider, String returnUrl, String key);

    CheckoutSession session(Caller caller, UUID sessionId);

    String portal(Caller caller, String returnUrl);

    record Result(CheckoutSession session, boolean replayed) {}
}
