package com.toefly.apigateway.application.port.out;

import reactor.core.publisher.Mono;

public interface WebSessionStorePort {

    /** Deletes every web session of the user; returns how many were deleted. */
    Mono<Long> deleteAllOf(String userId);
}
