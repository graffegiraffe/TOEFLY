package com.toefly.apigateway.adapter.in.web;

import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Until the email is verified only the profile is available (AC-01.02.3, {@code EmailVerifiedGate} of docs/10). */
@Component
@Order(30)
public class EmailVerifiedWebFilter implements WebFilter {

    private final ProblemResponses problems;

    public EmailVerifiedWebFilter(ProblemResponses problems) {
        this.problems = problems;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/v1/") || isProfileRead(exchange)) {
            return chain.filter(exchange);
        }
        return exchange.getPrincipal()
                .mapNotNull(Caller::of)
                .map(caller -> !caller.emailVerified())
                .defaultIfEmpty(false)
                .flatMap(blocked -> blocked
                        ? problems.forbidden(
                                exchange, "Verify your email to continue.", Map.of("reason", "EMAIL_NOT_VERIFIED"))
                        : chain.filter(exchange));
    }

    private static boolean isProfileRead(ServerWebExchange exchange) {
        return HttpMethod.GET.equals(exchange.getRequest().getMethod())
                && "/api/v1/me".equals(exchange.getRequest().getPath().value());
    }
}
