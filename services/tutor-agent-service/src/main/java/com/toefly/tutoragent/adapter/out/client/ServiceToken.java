package com.toefly.tutoragent.adapter.out.client;

import java.util.function.Supplier;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/** Access token of the {@code svc-tutor-agent-service} client for calls that do not go through a RestClient. */
public class ServiceToken implements Supplier<String> {

    private static final AnonymousAuthenticationToken SERVICE = new AnonymousAuthenticationToken(
            "toefly-service", "toefly-service", AuthorityUtils.createAuthorityList("ROLE_SERVICE"));

    private final OAuth2AuthorizedClientManager clients;
    private final String registration;

    public ServiceToken(OAuth2AuthorizedClientManager clients, String registration) {
        this.clients = clients;
        this.registration = registration;
    }

    @Override
    public String get() {
        OAuth2AuthorizedClient client = clients.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(registration)
                .principal(SERVICE)
                .build());
        if (client == null) {
            throw new IllegalStateException("No service token for " + registration);
        }
        return client.getAccessToken().getTokenValue();
    }
}
