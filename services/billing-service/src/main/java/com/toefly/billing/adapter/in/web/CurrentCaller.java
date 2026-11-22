package com.toefly.billing.adapter.in.web;

import com.toefly.billing.application.port.in.BillingException;
import com.toefly.billing.application.port.in.Caller;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** The caller of the current request: {@code sub}, roles and email of the token. */
@Component
public class CurrentCaller {

    public Caller get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("No authentication");
        }
        Set<Caller.Role> roles = EnumSet.noneOf(Caller.Role.class);
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            if (name != null && name.startsWith("ROLE_")) {
                try {
                    roles.add(Caller.Role.valueOf(name.substring("ROLE_".length())));
                } catch (IllegalArgumentException other) {
                    // roles of other services are not used here
                }
            }
        }
        String email = authentication instanceof JwtAuthenticationToken jwt
                ? jwt.getToken().getClaimAsString("email")
                : null;
        return new Caller(UUID.fromString(authentication.getName()), roles, email);
    }

    public Caller admin() {
        Caller caller = get();
        if (!caller.is(Caller.Role.ADMIN)) {
            throw BillingException.forbidden("This operation needs the ADMIN role.");
        }
        return caller;
    }
}
