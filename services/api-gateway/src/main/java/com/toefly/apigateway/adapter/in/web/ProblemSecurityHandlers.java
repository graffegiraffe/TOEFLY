package com.toefly.apigateway.adapter.in.web;

import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 401 and 403 of Spring Security as Problem Details instead of empty bodies or login redirects. */
@Component
public class ProblemSecurityHandlers implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private final ProblemResponses problems;

    public ProblemSecurityHandlers(ProblemResponses problems) {
        this.problems = problems;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        return problems.unauthenticated(exchange);
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return problems.forbidden(exchange, "You don't have access to this resource.", Map.of());
    }
}
