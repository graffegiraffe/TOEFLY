package com.toefly.apigateway.application.port.out;

import com.toefly.apigateway.domain.RateLimitTier;
import reactor.core.publisher.Mono;

/**
 * Plan-based rights of a user cached in Valkey as {@code ent:{userId}}; billing-service fills it in phase 11
 * (docs/05-api-contracts.md §2.9).
 */
public interface EntitlementCachePort {

    Mono<RateLimitTier> learnerTier(String userId);
}
