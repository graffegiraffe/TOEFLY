package com.toefly.apigateway.adapter.in.web;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * After the callback the session gets a new id (session fixation) and the browser returns to the app path chosen at
 * {@code /auth/login}; a cancelled or failed sign-in returns to the sign-in page (AC-01.03.4).
 */
public class BffAuthenticationHandlers
        implements ServerAuthenticationSuccessHandler, ServerAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(BffAuthenticationHandlers.class);

    private final String webOrigin;

    public BffAuthenticationHandlers(String webOrigin) {
        this.webOrigin = webOrigin;
    }

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        ServerWebExchange exchange = webFilterExchange.getExchange();
        return exchange.getSession().flatMap(session -> {
            String returnTo = session.getAttributeOrDefault(WebSessionAttributes.RETURN_TO, "/");
            session.getAttributes().remove(WebSessionAttributes.RETURN_TO);
            session.getAttributes().put(WebSessionAttributes.CSRF_TOKEN, WebSessionAttributes.newCsrfToken());
            return session.changeSessionId().then(redirect(exchange, webOrigin + returnTo));
        });
    }

    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange webFilterExchange, AuthenticationException exception) {
        // The reason stays in the log only: the browser gets the neutral sign-in page.
        log.warn("Web sign-in failed: {}", exception.getMessage());
        return redirect(webFilterExchange.getExchange(), webOrigin + "/sign-in");
    }

    private static Mono<Void> redirect(ServerWebExchange exchange, String location) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(location));
        return exchange.getResponse().setComplete();
    }
}
