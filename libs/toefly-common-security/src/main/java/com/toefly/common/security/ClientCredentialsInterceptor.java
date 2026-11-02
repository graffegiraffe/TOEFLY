package com.toefly.common.security;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Adds a service token of the {@code svc-<service>} Keycloak client to background calls (listeners, jobs) that have
 * no user token to forward.
 */
public class ClientCredentialsInterceptor implements ClientHttpRequestInterceptor {

    private static final AnonymousAuthenticationToken SERVICE_PRINCIPAL = new AnonymousAuthenticationToken(
            "toefly-service", "toefly-service", AuthorityUtils.createAuthorityList("ROLE_SERVICE"));

    private final OAuth2AuthorizedClientManager clientManager;
    private final String registrationId;

    public ClientCredentialsInterceptor(OAuth2AuthorizedClientManager clientManager, String registrationId) {
        this.clientManager = clientManager;
        this.registrationId = registrationId;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        OAuth2AuthorizedClient client =
                clientManager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
                        .principal(SERVICE_PRINCIPAL)
                        .build());
        if (client == null) {
            throw new IllegalStateException("No service token for client registration " + registrationId);
        }
        request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
        return execution.execute(request, body);
    }
}
