package com.toefly.apigateway.adapter.in.web;

import com.toefly.apigateway.application.port.in.RevokeSessionsUseCase;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Bearer tokens issued before "Sign out of all devices" or a deletion get 401 (AC-01.05.4). */
@Component
@Order(20)
public class RevokedTokenWebFilter implements WebFilter {

    private final RevokeSessionsUseCase revocations;
    private final ProblemResponses problems;

    public RevokedTokenWebFilter(RevokeSessionsUseCase revocations, ProblemResponses problems) {
        this.revocations = revocations;
        this.problems = problems;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getPrincipal()
                .mapNotNull(Caller::of)
                .filter(caller -> caller.tokenIssuedAt() != null)
                .flatMap(caller -> revocations.isRevoked(caller.userId(), caller.tokenIssuedAt()))
                .defaultIfEmpty(false)
                .flatMap(revoked -> revoked ? problems.unauthenticated(exchange) : chain.filter(exchange));
    }
}
