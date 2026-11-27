package com.toefly.tutoragent.adapter.in.web;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** The learner of the current request ({@code sub}) and their access token for one-hop calls to owners. */
@Component
public class CurrentUser {

    public UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("No authentication");
        }
        return UUID.fromString(authentication.getName());
    }

    public @Nullable String token() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwt
                ? jwt.getToken().getTokenValue()
                : null;
    }
}
