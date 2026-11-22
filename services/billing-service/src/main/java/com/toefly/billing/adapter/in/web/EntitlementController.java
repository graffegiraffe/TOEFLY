package com.toefly.billing.adapter.in.web;

import com.toefly.billing.adapter.in.web.api.EntitlementsApi;
import com.toefly.billing.adapter.in.web.dto.EntitlementSet;
import com.toefly.billing.application.port.in.EntitlementQueryUseCase;
import com.toefly.billing.application.port.in.EntitlementView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EntitlementController implements EntitlementsApi {

    private final EntitlementQueryUseCase entitlements;
    private final CurrentCaller caller;

    public EntitlementController(EntitlementQueryUseCase entitlements, CurrentCaller caller) {
        this.entitlements = entitlements;
        this.caller = caller;
    }

    @Override
    public ResponseEntity<EntitlementSet> getMyEntitlements() {
        EntitlementView view = entitlements.entitlements(caller.get().userId());
        return ResponseEntity.ok().eTag("\"" + view.version() + "\"").body(BillingDtoMapper.entitlements(view));
    }
}
