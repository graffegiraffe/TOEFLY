package com.toefly.apigateway.application.port.in;

import com.toefly.apigateway.domain.EndpointGroup;
import com.toefly.apigateway.domain.RateLimitDecision;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

public interface CheckRateLimitUseCase {

    /**
     * Takes tokens for one request: by user id when signed in, by client address otherwise. Roles pick the tutor and
     * admin tiers, the plan of the user picks the learner tier.
     */
    Mono<RateLimitDecision> check(
            @Nullable String userId, String clientAddress, Set<String> roles, EndpointGroup group);
}
