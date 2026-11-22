package com.toefly.billing.config;

import com.toefly.billing.adapter.out.client.HttpClassroomAdapter;
import com.toefly.billing.adapter.out.client.NoClassroomAdapter;
import com.toefly.billing.application.port.out.ClassroomPort;
import com.toefly.billing.application.service.BillingPolicy;
import com.toefly.billing.domain.saga.RetryPolicy;
import com.toefly.common.security.ClientCredentialsInterceptor;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BillingProperties.class)
class BillingConfig {

    static final String REGISTRATION = "svc-billing-service";

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    BillingPolicy billingPolicy(BillingProperties properties) {
        BillingProperties.Saga saga = properties.saga();
        return new BillingPolicy(
                properties.trialLength(),
                properties.trialEndingNotice(),
                properties.grace(),
                properties.trialReuseWindow(),
                properties.checkoutLifetime(),
                new RetryPolicy(saga.maxAttempts(), saga.firstDelay(), saga.maxDelay()),
                properties.returnUrls(),
                properties.webProviders(),
                properties.appStoreProducts() == null ? Map.of() : properties.appStoreProducts(),
                properties.identityPepper());
    }

    /** classroom-service arrives in phase 10; until then the saga steps of tutors have nothing to call. */
    @Bean
    ClassroomPort classroom(
            BillingProperties properties,
            RestClient.Builder builder,
            ObjectProvider<ClientRegistrationRepository> registrations,
            ObjectProvider<OAuth2AuthorizedClientService> authorizedClients) {
        if (!properties.classroom().enabled()) {
            return new NoClassroomAdapter();
        }
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                registrations.getObject(), authorizedClients.getObject());
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());
        return new HttpClassroomAdapter(classroomClient(properties, builder, manager));
    }

    /** docs/05 §9.2: billing → classroom, 500 ms / 3 s. */
    private static RestClient classroomClient(
            BillingProperties properties, RestClient.Builder builder, OAuth2AuthorizedClientManager clients) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return builder.clone()
                .baseUrl(properties.classroom().url())
                .requestFactory(factory)
                .requestInterceptor(new ClientCredentialsInterceptor(clients, REGISTRATION))
                .build();
    }
}
