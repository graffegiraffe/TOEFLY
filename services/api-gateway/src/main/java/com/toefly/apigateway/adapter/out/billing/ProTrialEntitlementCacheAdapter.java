package com.toefly.apigateway.adapter.out.billing;

import com.toefly.apigateway.application.port.out.EntitlementCachePort;
import com.toefly.apigateway.domain.RateLimitTier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Until billing-service fills {@code ent:{userId}} (phase 11) every learner is on the 7-day Pro trial, so every
 * right is granted and the Pro limits apply (docs/12-implementation-plan.md, phase 1).
 */
@Component
public class ProTrialEntitlementCacheAdapter implements EntitlementCachePort {

    @Override
    public Mono<RateLimitTier> learnerTier(String userId) {
        return Mono.just(RateLimitTier.STUDENT_PRO);
    }
}
