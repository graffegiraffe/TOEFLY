package com.toefly.apigateway.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Changing requests signed in with the session cookie need the web origin and the session's {@code X-CSRF-Token}
 * (docs/10-security-privacy.md §4.2). Bearer requests are not checked: browsers never send that header by
 * themselves.
 */
@Component
@Order(10)
public class CsrfWebFilter implements WebFilter {

    static final String HEADER = "X-CSRF-Token";
    private static final Set<HttpMethod> SAFE = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

    private final String webOrigin;
    private final ProblemResponses problems;

    public CsrfWebFilter(@Value("${toefly.gateway.web-origin}") String webOrigin, ProblemResponses problems) {
        this.webOrigin = webOrigin;
        this.problems = problems;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (SAFE.contains(exchange.getRequest().getMethod())
                || exchange.getRequest().getPath().value().startsWith("/internal/")) {
            return chain.filter(exchange);
        }
        return exchange.getPrincipal()
                .mapNotNull(Caller::of)
                .filter(caller -> caller.web())
                .flatMap(caller -> check(exchange))
                .switchIfEmpty(Mono.just(true))
                .flatMap(allowed -> allowed
                        ? chain.filter(exchange)
                        : problems.forbidden(
                                exchange, "The request did not come from the TOEFLY app.", Map.of("reason", "CSRF")));
    }

    private Mono<Boolean> check(ServerWebExchange exchange) {
        var headers = exchange.getRequest().getHeaders();
        String origin = headers.getOrigin();
        boolean sameSite = origin == null
                ? Set.of("same-origin", "same-site").contains(headers.getFirst("Sec-Fetch-Site"))
                : webOrigin.equals(origin);
        if (!sameSite) {
            return Mono.just(false);
        }
        String sent = headers.getFirst(HEADER);
        return exchange.getSession().map(session -> {
            String expected = session.getAttribute(WebSessionAttributes.CSRF_TOKEN);
            return expected != null
                    && sent != null
                    && MessageDigest.isEqual(
                            expected.getBytes(StandardCharsets.UTF_8), sent.getBytes(StandardCharsets.UTF_8));
        });
    }
}
