package com.toefly.apigateway.adapter.in.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

/** Adds the realm roles of the access token ({@code realm_access.roles}) to the web session user. */
public class KeycloakOidcUserService implements ReactiveOAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcReactiveOAuth2UserService delegate = new OidcReactiveOAuth2UserService();
    private final ReactiveJwtDecoder accessTokens;

    public KeycloakOidcUserService(ReactiveJwtDecoder accessTokens) {
        this.accessTokens = accessTokens;
    }

    @Override
    public Mono<OidcUser> loadUser(OidcUserRequest request) {
        return delegate.loadUser(request)
                .zipWith(accessTokens.decode(request.getAccessToken().getTokenValue()), (user, jwt) -> {
                    List<GrantedAuthority> authorities = new ArrayList<>(user.getAuthorities());
                    Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
                    if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
                        for (Object role : roles) {
                            String name = String.valueOf(role);
                            if (List.of("STUDENT", "TUTOR", "ADMIN").contains(name)) {
                                authorities.add(new SimpleGrantedAuthority("ROLE_" + name));
                            }
                        }
                    }
                    return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo(), "sub");
                });
    }
}
