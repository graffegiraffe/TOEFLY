package com.toefly.practice.config;

import com.toefly.common.security.ClientCredentialsInterceptor;
import com.toefly.practice.adapter.out.client.ContentItemSetClientAdapter;
import com.toefly.practice.application.port.out.ContentItemSetPort;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class PracticeConfig {

    static final String REGISTRATION = "svc-practice-service";

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Client credentials for calls without a user token (docs/10-security-privacy.md §7.2). */
    @Bean
    OAuth2AuthorizedClientManager serviceClientManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());
        return manager;
    }

    /** content-service: the item set with keys; timeouts per docs/04-architecture.md §11.1. */
    @Bean
    ContentItemSetPort contentItemSets(
            RestClient.Builder builder,
            OAuth2AuthorizedClientManager clients,
            @Value("${toefly.practice.content-url}") String contentUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return new ContentItemSetClientAdapter(builder.clone()
                .baseUrl(contentUrl)
                .requestFactory(factory)
                .requestInterceptor(new ClientCredentialsInterceptor(clients, REGISTRATION))
                .build());
    }
}
