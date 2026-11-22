package com.toefly.billing.adapter.in.web;

import com.toefly.billing.adapter.in.web.api.AdminBillingApi;
import com.toefly.billing.adapter.in.web.dto.AdminSubscriptionPage;
import com.toefly.billing.adapter.in.web.dto.EntitlementSet;
import com.toefly.billing.adapter.in.web.dto.ManualGrantRequest;
import com.toefly.billing.adapter.in.web.dto.PaymentProviderCode;
import com.toefly.billing.adapter.in.web.dto.PlanTierCode;
import com.toefly.billing.adapter.in.web.dto.SagaInstance;
import com.toefly.billing.adapter.in.web.dto.SagaInstancePage;
import com.toefly.billing.adapter.in.web.dto.SagaStatus;
import com.toefly.billing.adapter.in.web.dto.SubscriptionStatus;
import com.toefly.billing.application.port.in.AdminBillingUseCase;
import com.toefly.billing.application.port.in.Caller;
import com.toefly.billing.application.port.in.PaymentSagaUseCase;
import com.toefly.billing.domain.saga.PaymentSaga;
import com.toefly.billing.domain.subscription.Subscription;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Admin section of billing; every operation needs the ADMIN role (the gateway checks it too). */
@RestController
public class AdminBillingController implements AdminBillingApi {

    private final AdminBillingUseCase admin;
    private final PaymentSagaUseCase sagas;
    private final CurrentCaller caller;
    private final Idempotent idempotent;

    public AdminBillingController(
            AdminBillingUseCase admin, PaymentSagaUseCase sagas, CurrentCaller caller, Idempotent idempotent) {
        this.admin = admin;
        this.sagas = sagas;
        this.caller = caller;
        this.idempotent = idempotent;
    }

    @Override
    public ResponseEntity<AdminSubscriptionPage> listSubscriptions(
            @Nullable SubscriptionStatus status,
            @Nullable PlanTierCode tier,
            @Nullable PaymentProviderCode provider,
            Integer limit,
            @Nullable String cursor) {
        caller.admin();
        Cursors.Position position = Cursors.decode(cursor);
        AdminBillingUseCase.SubscriptionPage page = admin.subscriptions(
                status == null
                        ? null
                        : com.toefly.billing.domain.subscription.SubscriptionStatus.valueOf(status.getValue()),
                tier == null ? null : com.toefly.billing.domain.plan.PlanTierCode.valueOf(tier.getValue()),
                provider == null
                        ? null
                        : com.toefly.billing.domain.subscription.PaymentProviderCode.valueOf(provider.getValue()),
                position == null ? null : position.at(),
                position == null ? null : position.id(),
                limit);
        List<Subscription> items = page.items();
        Subscription last = items.isEmpty() ? null : items.get(items.size() - 1);
        return ResponseEntity.ok(new AdminSubscriptionPage()
                .items(items.stream().map(BillingDtoMapper::admin).toList())
                .nextCursor(page.more() && last != null ? Cursors.encode(last.createdAt(), last.id()) : null));
    }

    @Override
    public ResponseEntity<EntitlementSet> grantPlan(UUID userId, String idempotencyKey, ManualGrantRequest request) {
        Caller current = caller.admin();
        return idempotent.run(
                current.userId(),
                idempotencyKey,
                "grantPlan:" + userId,
                request,
                EntitlementSet.class,
                HttpStatus.OK,
                () -> BillingDtoMapper.entitlements(admin.grant(
                        userId,
                        com.toefly.billing.domain.plan.PlanTierCode.valueOf(
                                request.getPlanTierCode().getValue()),
                        request.getUntil().toInstant(),
                        request.getReason())));
    }

    @Override
    public ResponseEntity<SagaInstancePage> listSagas(
            @Nullable SagaStatus status, Integer limit, @Nullable String cursor) {
        caller.admin();
        Cursors.Position position = Cursors.decode(cursor);
        PaymentSagaUseCase.SagaPage page = sagas.page(
                status == null ? null : com.toefly.billing.domain.saga.SagaStatus.valueOf(status.getValue()),
                position == null ? null : position.at(),
                position == null ? null : position.id(),
                limit);
        List<PaymentSaga> items = page.items();
        PaymentSaga last = items.isEmpty() ? null : items.get(items.size() - 1);
        return ResponseEntity.ok(new SagaInstancePage()
                .items(items.stream().map(BillingDtoMapper::saga).toList())
                .nextCursor(page.more() && last != null ? Cursors.encode(last.updatedAt(), last.id()) : null));
    }

    @Override
    public ResponseEntity<SagaInstance> retrySaga(UUID sagaId, String idempotencyKey) {
        Caller current = caller.admin();
        return idempotent.run(
                current.userId(),
                idempotencyKey,
                "retrySaga:" + sagaId,
                null,
                SagaInstance.class,
                HttpStatus.OK,
                () -> BillingDtoMapper.saga(sagas.retry(sagaId)));
    }
}
