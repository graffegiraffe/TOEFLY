package com.toefly.billing.adapter.in.web;

import com.toefly.billing.adapter.in.web.api.AppStoreApi;
import com.toefly.billing.adapter.in.web.dto.AppStoreTransactionRequest;
import com.toefly.billing.adapter.in.web.dto.Subscription;
import com.toefly.billing.application.port.in.AppStoreUseCase;
import com.toefly.billing.application.port.in.Caller;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppStoreController implements AppStoreApi {

    private final AppStoreUseCase appStore;
    private final CurrentCaller caller;
    private final Idempotent idempotent;

    public AppStoreController(AppStoreUseCase appStore, CurrentCaller caller, Idempotent idempotent) {
        this.appStore = appStore;
        this.caller = caller;
        this.idempotent = idempotent;
    }

    @Override
    public ResponseEntity<Subscription> submitAppStoreTransaction(
            String idempotencyKey, AppStoreTransactionRequest request) {
        Caller current = caller.get();
        return idempotent.run(
                current.userId(),
                idempotencyKey,
                "submitAppStoreTransaction",
                request,
                Subscription.class,
                HttpStatus.OK,
                () -> BillingDtoMapper.subscription(appStore.submit(current, request.getSignedTransactionInfo())));
    }
}
