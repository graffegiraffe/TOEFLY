package com.toefly.billing.adapter.in.web;

import com.toefly.billing.adapter.in.web.api.PlanTiersApi;
import com.toefly.billing.adapter.in.web.dto.PlanTierList;
import com.toefly.billing.application.port.in.PlanCatalogUseCase;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlanTierController implements PlanTiersApi {

    private final PlanCatalogUseCase catalog;

    public PlanTierController(PlanCatalogUseCase catalog) {
        this.catalog = catalog;
    }

    @Override
    public ResponseEntity<PlanTierList> listPlanTiers() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(BillingDtoMapper.catalog(catalog.catalog()));
    }
}
