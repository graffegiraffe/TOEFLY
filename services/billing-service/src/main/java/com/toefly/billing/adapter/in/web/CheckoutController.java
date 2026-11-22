package com.toefly.billing.adapter.in.web;

import com.toefly.billing.adapter.in.web.api.CheckoutApi;
import com.toefly.billing.adapter.in.web.dto.CheckoutSession;
import com.toefly.billing.adapter.in.web.dto.CreateCheckoutSessionRequest;
import com.toefly.billing.adapter.in.web.dto.CreatePortalSessionRequest;
import com.toefly.billing.adapter.in.web.dto.PortalSession;
import com.toefly.billing.application.port.in.Caller;
import com.toefly.billing.application.port.in.CheckoutUseCase;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckoutController implements CheckoutApi {

    private final CheckoutUseCase checkout;
    private final CurrentCaller caller;
    private final Idempotent idempotent;

    public CheckoutController(CheckoutUseCase checkout, CurrentCaller caller, Idempotent idempotent) {
        this.checkout = checkout;
        this.caller = caller;
        this.idempotent = idempotent;
    }

    /** The checkout session itself is unique per Idempotency-Key, so a retry gets the same provider page. */
    @Override
    public ResponseEntity<CheckoutSession> createCheckoutSession(
            String idempotencyKey, CreateCheckoutSessionRequest request) {
        CheckoutUseCase.Result result = checkout.create(
                caller.get(),
                PlanTierCode.valueOf(request.getPlanTierCode().getValue()),
                PaymentProviderCode.valueOf(request.getProvider().getValue()),
                request.getReturnUrl(),
                idempotencyKey);
        var response = ResponseEntity.created(
                URI.create("/api/v1/checkout-sessions/" + result.session().id()));
        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(BillingDtoMapper.session(result.session()));
    }

    @Override
    public ResponseEntity<CheckoutSession> getCheckoutSession(UUID sessionId) {
        return ResponseEntity.ok(BillingDtoMapper.session(checkout.session(caller.get(), sessionId)));
    }

    @Override
    public ResponseEntity<PortalSession> createPortalSession(
            String idempotencyKey, CreatePortalSessionRequest request) {
        Caller current = caller.get();
        return idempotent.run(
                current.userId(),
                idempotencyKey,
                "createPortalSession",
                request,
                PortalSession.class,
                HttpStatus.CREATED,
                () -> new PortalSession().url(checkout.portal(current, request.getReturnUrl())));
    }
}
