package com.toefly.apigateway.adapter.in.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Security headers of every API response (docs/10-security-privacy.md §8.6). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SecurityHeadersWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set("Strict-Transport-Security", "max-age=15552000; includeSubDomains");
            headers.set("Cross-Origin-Resource-Policy", "same-site");
            headers.remove("X-Frame-Options");
            headers.set("X-Frame-Options", "DENY");
            if (!headers.containsHeader(HttpHeaders.CACHE_CONTROL)) {
                headers.setCacheControl("no-store");
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
