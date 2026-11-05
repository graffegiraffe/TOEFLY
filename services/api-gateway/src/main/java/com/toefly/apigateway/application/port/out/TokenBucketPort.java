package com.toefly.apigateway.application.port.out;

import com.toefly.apigateway.domain.RateLimitDecision;
import com.toefly.apigateway.domain.TokenBucket;
import reactor.core.publisher.Mono;

public interface TokenBucketPort {

    /** Takes the tokens of one request from the bucket under {@code key}; errors when Valkey is unavailable. */
    Mono<RateLimitDecision> take(String key, TokenBucket bucket);
}
