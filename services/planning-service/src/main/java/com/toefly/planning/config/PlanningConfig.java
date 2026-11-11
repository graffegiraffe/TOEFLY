package com.toefly.planning.config;

import com.toefly.common.security.ClientCredentialsInterceptor;
import com.toefly.planning.adapter.out.client.ContentUnitCatalogAdapter;
import com.toefly.planning.application.port.out.UnitCatalogPort;
import com.toefly.planning.application.service.PlanningSettings;
import com.toefly.planning.domain.composition.CompositionRules;
import com.toefly.planning.domain.composition.ResourceLink;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
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
@EnableConfigurationProperties(PlanningProperties.class)
class PlanningConfig {

    static final String REGISTRATION = "svc-planning-service";

    /** Every rule of the week reads the time from this clock; only local and demo can move it. */
    @Bean
    @Profile({"local", "demo"})
    ShiftableClock shiftableClock() {
        return new ShiftableClock(Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    CompositionRules compositionRules(PlanningProperties properties) {
        PlanningProperties.Composition c = properties.composition();
        return new CompositionRules(
                c.baseMinutesPerDay(),
                c.baseCounts(),
                c.minutes(),
                c.weakFactor(),
                c.budgetMaxFactor(),
                c.budgetMinFactor(),
                c.unitMinutes(),
                c.vocabularyReviewMinutes(),
                c.resourceMinutes(),
                c.mockExamMinutes(),
                c.minimalModeMinutes());
    }

    @Bean
    PlanningSettings planningSettings(PlanningProperties properties) {
        return new PlanningSettings(
                properties.availableTaskTypes(),
                properties.vocabularyReviewEnabled(),
                properties.mockExamEnabled(),
                properties.examFormat(),
                properties.resources().stream()
                        .map(resource -> new ResourceLink(resource.url(), resource.title(), resource.sections()))
                        .toList());
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

    /** content-service: units of the book tracks; timeouts per docs/05-api-contracts.md §9.1 (500 ms / 2 s). */
    @Bean
    UnitCatalogPort unitCatalog(
            RestClient.Builder builder, OAuth2AuthorizedClientManager clients, PlanningProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofSeconds(2));
        return new ContentUnitCatalogAdapter(builder.clone()
                .baseUrl(properties.contentUrl())
                .requestFactory(factory)
                .requestInterceptor(new ClientCredentialsInterceptor(clients, REGISTRATION))
                .build());
    }
}
