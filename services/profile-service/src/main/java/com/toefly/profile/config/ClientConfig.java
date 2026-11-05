package com.toefly.profile.config;

import com.toefly.common.security.ClientCredentialsInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

/** Outgoing calls with the service token of {@code svc-profile-service}. */
@Configuration(proxyBeanMethods = false)
public class ClientConfig {

    static final String REGISTRATION = "svc-profile-service";

    /** Client credentials outside of a web request: listeners and jobs use the same manager. */
    @Bean
    OAuth2AuthorizedClientManager serviceClientManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());
        return manager;
    }

    @Bean
    RestClient keycloakAdminRestClient(
            RestClient.Builder builder,
            OAuth2AuthorizedClientManager clientManager,
            @Value("${toefly.profile.keycloak-admin-url}") String adminUrl) {
        return builder.clone()
                .baseUrl(adminUrl)
                .requestInterceptor(new ClientCredentialsInterceptor(clientManager, REGISTRATION))
                .build();
    }

    @Bean
    RestClient gatewayRestClient(
            RestClient.Builder builder,
            OAuth2AuthorizedClientManager clientManager,
            @Value("${toefly.profile.gateway-url}") String gatewayUrl) {
        return builder.clone()
                .baseUrl(gatewayUrl)
                .requestInterceptor(new ClientCredentialsInterceptor(clientManager, REGISTRATION))
                .build();
    }
}
