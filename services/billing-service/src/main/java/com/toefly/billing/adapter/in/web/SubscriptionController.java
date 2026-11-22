package com.toefly.billing.adapter.in.web;

import com.toefly.billing.adapter.in.web.api.SubscriptionsApi;
import com.toefly.billing.adapter.in.web.dto.CancelSubscriptionRequest;
import com.toefly.billing.adapter.in.web.dto.ChangePlanRequest;
import com.toefly.billing.adapter.in.web.dto.PaymentList;
import com.toefly.billing.adapter.in.web.dto.Subscription;
import com.toefly.billing.application.port.in.Caller;
import com.toefly.billing.application.port.in.SubscriptionUseCase;
import com.toefly.billing.domain.plan.PlanTierCode;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SubscriptionController implements SubscriptionsApi {

    private final SubscriptionUseCase subscriptions;
    private final CurrentCaller caller;
    private final Idempotent idempotent;

    public SubscriptionController(SubscriptionUseCase subscriptions, CurrentCaller caller, Idempotent idempotent) {
        this.subscriptions = subscriptions;
        this.caller = caller;
        this.idempotent = idempotent;
    }

    @Override
    public ResponseEntity<Subscription> getCurrentSubscription() {
        return ResponseEntity.ok(BillingDtoMapper.subscription(subscriptions.current(caller.get())));
    }

    @Override
    public ResponseEntity<Subscription> cancelSubscription(
            String idempotencyKey, @Nullable CancelSubscriptionRequest request) {
        Caller current = caller.get();
        return idempotent.run(
                current.userId(),
                idempotencyKey,
                "cancelSubscription",
                request,
                Subscription.class,
                HttpStatus.OK,
                () -> BillingDtoMapper.subscription(subscriptions.cancel(current)));
    }

    @Override
    public ResponseEntity<Subscription> resumeSubscription() {
        return ResponseEntity.ok(BillingDtoMapper.subscription(subscriptions.resume(caller.get())));
    }

    @Override
    public ResponseEntity<Subscription> changePlan(String idempotencyKey, ChangePlanRequest request) {
        Caller current = caller.get();
        return idempotent.run(
                current.userId(),
                idempotencyKey,
                "changePlan",
                request,
                Subscription.class,
                HttpStatus.OK,
                () -> BillingDtoMapper.subscription(subscriptions.changePlan(
                        current, PlanTierCode.valueOf(request.getPlanTierCode().getValue()))));
    }

    @Override
    public ResponseEntity<PaymentList> listMyPayments() {
        return ResponseEntity.ok(new PaymentList()
                .items(subscriptions.payments(caller.get()).stream()
                        .map(BillingDtoMapper::payment)
                        .toList()));
    }
}
