package com.toefly.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class InternalUserAccessTest {

    private static final String USER = "7b1c9a44-0f3e-4f0b-9a60-6a53f1d2c871";
    private static final RequestAuthorizationContext OWN =
            new RequestAuthorizationContext(new MockHttpServletRequest(), Map.of("userId", USER));

    private static Authentication token(String name, String... authorities) {
        TestingAuthenticationToken token = new TestingAuthenticationToken(name, "n/a", authorities);
        token.setAuthenticated(true);
        return token;
    }

    private static boolean own(Authentication who) {
        return InternalUserAccess.serviceOrSubject().authorize(() -> who, OWN).isGranted();
    }

    private static boolean any(Authentication who) {
        return InternalUserAccess.serviceOrUser().authorize(() -> who, OWN).isGranted();
    }

    @Test
    void ownPathsAcceptTheServiceTokenOrTheSubject() {
        assertThat(own(token("svc-tutor-agent", "ROLE_SERVICE"))).isTrue();
        assertThat(own(token(USER, "ROLE_STUDENT"))).isTrue();
        assertThat(own(token("4f6c0d1e-9b2a-4c3d-8e7f-0a1b2c3d4e5f", "ROLE_STUDENT")))
                .isFalse();
        assertThat(own(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))))
                .isFalse();
        assertThat(InternalUserAccess.serviceOrSubject()
                        .authorize(() -> null, OWN)
                        .isGranted())
                .isFalse();
    }

    @Test
    void userPathsAcceptTheServiceTokenOrAnyRealmRole() {
        assertThat(any(token("svc-tutor-agent", "ROLE_SERVICE"))).isTrue();
        assertThat(any(token(USER, "ROLE_TUTOR"))).isTrue();
        assertThat(any(token(USER, "ROLE_OTHER"))).isFalse();
        assertThat(any(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))))
                .isFalse();
    }

    @Test
    void ownPathsNeedTheUserIdVariable() {
        assertThatThrownBy(() -> new InternalUserAccess(List.of("/internal/v1/users/{id}/x"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new InternalUserAccess(List.of("/internal/v1/users/{userId}/x"), List.of("/internal/v1/a/*"))
                        .userPaths())
                .containsExactly("/internal/v1/a/*");
    }
}
