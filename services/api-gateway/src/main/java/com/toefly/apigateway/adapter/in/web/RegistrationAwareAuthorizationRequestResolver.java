package com.toefly.apigateway.adapter.in.web;

import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Authorization Code with PKCE S256 for the confidential BFF client; with {@code ?register} the request goes to
 * the Keycloak registration endpoint instead of the sign-in page; with {@code ?reauth} Keycloak asks for the
 * credentials again ({@code prompt=login}), which renews {@code auth_time} before account deletion (AC-31.04.1).
 */
public class RegistrationAwareAuthorizationRequestResolver implements ServerOAuth2AuthorizationRequestResolver {

    static final String PARAMETER = "register";
    static final String REAUTH = "reauth";

    private final DefaultServerOAuth2AuthorizationRequestResolver delegate;

    public RegistrationAwareAuthorizationRequestResolver(ReactiveClientRegistrationRepository registrations) {
        this.delegate = new DefaultServerOAuth2AuthorizationRequestResolver(registrations);
        // Interface texts are English (docs/02 §0.3) whatever language the browser prefers.
        this.delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce()
                .andThen(builder -> builder.parameters(parameters -> parameters.put("ui_locales", "en"))));
    }

    @Override
    public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange) {
        return delegate.resolve(exchange).map(request -> customize(exchange, request));
    }

    @Override
    public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange, String clientRegistrationId) {
        return delegate.resolve(exchange, clientRegistrationId).map(request -> customize(exchange, request));
    }

    private static OAuth2AuthorizationRequest customize(
            ServerWebExchange exchange, OAuth2AuthorizationRequest request) {
        var query = exchange.getRequest().getQueryParams();
        if (query.containsKey(REAUTH)) {
            return OAuth2AuthorizationRequest.from(request)
                    .additionalParameters(parameters -> parameters.put("prompt", "login"))
                    .build();
        }
        if (!query.containsKey(PARAMETER)) {
            return request;
        }
        String registrations = request.getAuthorizationUri()
                .replace("/protocol/openid-connect/auth", "/protocol/openid-connect/registrations");
        return OAuth2AuthorizationRequest.from(request)
                .authorizationUri(registrations)
                .build();
    }
}
