package com.toefly.common.security;

import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Internal operations marked {@code CC|JWT} (docs/05-api-contracts.md §9.1, §9.3): besides the service token they
 * accept the JWT of the user whose request a tool of tutor-agent-service serves. A service declares a bean of this
 * type and the default chain puts these paths before the {@code /internal/**} rule.
 *
 * @param ownPaths path patterns with the variable {@code {userId}}: the service token or a JWT with
 *     {@code sub == userId}
 * @param userPaths path patterns that any user JWT may call: the service checks ownership itself (a resource of
 *     another user is {@code 404}) or the data is not personal
 */
public record InternalUserAccess(List<String> ownPaths, List<String> userPaths) {

    static final String USER_ID = "userId";
    static final String SERVICE = KeycloakRoleConverter.SERVICE_AUTHORITY;

    public InternalUserAccess {
        ownPaths = List.copyOf(ownPaths);
        userPaths = List.copyOf(userPaths);
        for (String path : ownPaths) {
            if (!path.contains("{" + USER_ID + "}")) {
                throw new IllegalArgumentException("an own path needs the variable {userId}: " + path);
            }
        }
    }

    /** The service token, or a JWT whose subject is the {@code userId} of the path. */
    public static AuthorizationManager<RequestAuthorizationContext> serviceOrSubject() {
        return new AuthorizationManager<>() {
            @Override
            public AuthorizationResult authorize(
                    Supplier<? extends @Nullable Authentication> authentication, RequestAuthorizationContext context) {
                Authentication who = authentication.get();
                if (anonymous(who)) {
                    return new AuthorizationDecision(false);
                }
                return new AuthorizationDecision(service(who)
                        || who.getName().equals(context.getVariables().get(USER_ID)));
            }
        };
    }

    /** The service token, or the JWT of any user with a realm role. */
    public static AuthorizationManager<RequestAuthorizationContext> serviceOrUser() {
        return new AuthorizationManager<>() {
            @Override
            public AuthorizationResult authorize(
                    Supplier<? extends @Nullable Authentication> authentication, RequestAuthorizationContext context) {
                Authentication who = authentication.get();
                if (anonymous(who)) {
                    return new AuthorizationDecision(false);
                }
                return new AuthorizationDecision(service(who) || user(who));
            }
        };
    }

    private static boolean anonymous(@Nullable Authentication who) {
        return who == null || !who.isAuthenticated() || who instanceof AnonymousAuthenticationToken;
    }

    private static boolean service(Authentication who) {
        return who.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(SERVICE::equals);
    }

    private static boolean user(Authentication who) {
        for (GrantedAuthority authority : who.getAuthorities()) {
            for (ToeflyRole role : ToeflyRole.values()) {
                if (role.authority().equals(authority.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }
}
