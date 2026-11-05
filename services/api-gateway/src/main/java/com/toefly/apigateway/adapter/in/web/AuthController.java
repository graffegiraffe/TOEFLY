package com.toefly.apigateway.adapter.in.web;

import com.toefly.apigateway.domain.ReturnPath;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * BFF endpoints of the web client (docs/10-security-privacy.md §4.2): the browser gets only the session cookie,
 * tokens stay in the session in Valkey.
 */
@RestController
public class AuthController {

    static final String AUTHORIZATION_PATH = "/oauth2/authorization/keycloak";

    private final String webOrigin;
    private final String endSessionUri;

    public AuthController(
            @Value("${toefly.gateway.web-origin}") String webOrigin,
            @Value("${toefly.gateway.keycloak.end-session-uri}") String endSessionUri) {
        this.webOrigin = webOrigin;
        this.endSessionUri = endSessionUri;
    }

    @GetMapping("/auth/login")
    public Mono<ResponseEntity<Void>> login(
            @RequestParam(required = false) @Nullable String returnTo, WebSession session) {
        return start(session, returnTo, AUTHORIZATION_PATH);
    }

    /** Same as sign-in, but Keycloak opens its registration page. */
    @GetMapping("/auth/register")
    public Mono<ResponseEntity<Void>> register(
            @RequestParam(required = false) @Nullable String returnTo, WebSession session) {
        return start(
                session, returnTo, AUTHORIZATION_PATH + "?" + RegistrationAwareAuthorizationRequestResolver.PARAMETER);
    }

    @GetMapping("/auth/session")
    public Mono<ResponseEntity<Map<String, Object>>> session(
            @AuthenticationPrincipal OidcUser user,
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient client,
            ServerWebExchange exchange) {
        return exchange.getSession().map(session -> {
            String csrf = session.getAttributeOrDefault(WebSessionAttributes.CSRF_TOKEN, "");
            if (csrf.isEmpty()) {
                csrf = WebSessionAttributes.newCsrfToken();
                session.getAttributes().put(WebSessionAttributes.CSRF_TOKEN, csrf);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("authenticated", true);
            body.put(
                    "user",
                    Map.of(
                            "id", user.getSubject(),
                            "roles", roles(user),
                            "emailVerified", Boolean.TRUE.equals(user.getEmailVerified())));
            body.put("csrfToken", csrf);
            Instant expiresAt = client.getAccessToken().getExpiresAt();
            if (expiresAt != null) {
                body.put("expiresAt", expiresAt.toString());
            }
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(body);
        });
    }

    /** Ends the web session; the app then opens the Keycloak logout page to end the Keycloak session too. */
    @PostMapping("/auth/logout")
    public Mono<ResponseEntity<Map<String, String>>> logout(
            @AuthenticationPrincipal OidcUser user, WebSession session) {
        String logoutUrl = UriComponentsBuilder.fromUriString(endSessionUri)
                .queryParam("id_token_hint", user.getIdToken().getTokenValue())
                .queryParam("post_logout_redirect_uri", webOrigin + "/")
                .encode()
                .build()
                .toUriString();
        return session.invalidate().thenReturn(ResponseEntity.ok(Map.of("logoutUrl", logoutUrl)));
    }

    private Mono<ResponseEntity<Void>> start(WebSession session, @Nullable String returnTo, String authorization) {
        session.getAttributes()
                .put(WebSessionAttributes.RETURN_TO, ReturnPath.parse(returnTo).value());
        return Mono.just(ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authorization))
                .build());
    }

    private static java.util.List<String> roles(OidcUser user) {
        return user.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .sorted()
                .toList();
    }
}
