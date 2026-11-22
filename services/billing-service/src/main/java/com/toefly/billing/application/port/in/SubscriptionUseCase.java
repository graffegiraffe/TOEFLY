package com.toefly.billing.application.port.in;

import com.toefly.billing.domain.payment.Payment;
import com.toefly.billing.domain.plan.PlanTierCode;
import java.util.List;

public interface SubscriptionUseCase {

    SubscriptionView current(Caller caller);

    SubscriptionView cancel(Caller caller);

    SubscriptionView resume(Caller caller);

    SubscriptionView changePlan(Caller caller, PlanTierCode target);

    List<Payment> payments(Caller caller);
}
