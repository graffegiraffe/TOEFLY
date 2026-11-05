package com.toefly.apigateway.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.web.server.MockWebSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

class CsrfWebFilterTest {

    static final String WEB = "http://app.toefly.localhost:5173";

    final CsrfWebFilter filter =
            new CsrfWebFilter(WEB, new ProblemResponses(JsonMapper.builder().build()));

    boolean passed;
    final WebFilterChain chain = exchange -> {
        passed = true;
        return Mono.empty();
    };

    static ServerWebExchange webExchange(MockServerHttpRequest.BaseBuilder<?> request, String sessionToken) {
        MockWebSession session = new MockWebSession();
        session.getAttributes().put(WebSessionAttributes.CSRF_TOKEN, sessionToken);
        var user = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")),
                new OidcIdToken("id", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "u1")));
        var authentication = new OAuth2AuthenticationToken(user, user.getAuthorities(), "keycloak");
        return MockServerWebExchange.builder(request.build())
                .session(session)
                .build()
                .mutate()
                .principal(Mono.just(authentication))
                .build();
    }

    @Test
    @DisplayName("ADR-0023 a cookie request with the web origin and the session token passes")
    void validRequestPasses() {
        var exchange = webExchange(
                MockServerHttpRequest.put("/api/v1/me/preferences")
                        .header("Origin", WEB)
                        .header(CsrfWebFilter.HEADER, "secret"),
                "secret");

        filter.filter(exchange, chain).block();

        assertThat(passed).isTrue();
    }

    @Test
    @DisplayName("ADR-0023 a missing or wrong CSRF token is rejected")
    void wrongTokenIsRejected() {
        var exchange = webExchange(
                MockServerHttpRequest.put("/api/v1/me/preferences")
                        .header("Origin", WEB)
                        .header(CsrfWebFilter.HEADER, "guess"),
                "secret");

        filter.filter(exchange, chain).block();

        assertThat(passed).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("ADR-0023 a foreign origin is rejected even with the token")
    void foreignOriginIsRejected() {
        var exchange = webExchange(
                MockServerHttpRequest.post("/api/v1/me/data-exports")
                        .header("Origin", "https://evil.example")
                        .header(CsrfWebFilter.HEADER, "secret"),
                "secret");

        filter.filter(exchange, chain).block();

        assertThat(passed).isFalse();
    }

    @Test
    void withoutOriginSecFetchSiteDecides() {
        var exchange = webExchange(
                MockServerHttpRequest.post("/auth/logout")
                        .header("Sec-Fetch-Site", "same-site")
                        .header(CsrfWebFilter.HEADER, "secret"),
                "secret");

        filter.filter(exchange, chain).block();

        assertThat(passed).isTrue();
    }

    @Test
    void safeAndBearerRequestsAreNotChecked() {
        filter.filter(webExchange(MockServerHttpRequest.get("/api/v1/me"), "secret"), chain)
                .block();
        assertThat(passed).isTrue();

        passed = false;
        filter.filter(MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/me/data-exports")), chain)
                .block();
        assertThat(passed).isTrue();
    }
}
