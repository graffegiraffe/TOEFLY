package com.toefly.assessment.config;

import com.toefly.assessment.adapter.out.client.AiGatewayGradingAdapter;
import com.toefly.assessment.adapter.out.client.ContentTaskAdapter;
import com.toefly.assessment.application.port.out.AiGradingPort;
import com.toefly.assessment.application.port.out.TaskContentPort;
import com.toefly.assessment.application.service.GradingKit;
import com.toefly.assessment.domain.grading.Taxonomy;
import com.toefly.assessment.domain.submission.TaskType;
import com.toefly.common.security.ClientCredentialsInterceptor;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(AssessmentProperties.class)
class AssessmentConfig {

    static final String REGISTRATION = "svc-assessment-service";

    @Bean
    @ConditionalOnMissingBean(Clock.class)
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

    /** content-service; timeouts per docs/05-api-contracts.md §9.1 (500 ms / 2 s). */
    @Bean
    TaskContentPort taskContent(
            RestClient.Builder builder, OAuth2AuthorizedClientManager clients, AssessmentProperties properties) {
        return new ContentTaskAdapter(client(builder, clients, properties.contentUrl(), Duration.ofSeconds(2)));
    }

    /** ai-gateway, background group: 1 s to connect, 90 s to read (docs/05-api-contracts.md §9.3.8). */
    @Bean
    AiGradingPort aiGrading(
            RestClient.Builder builder, OAuth2AuthorizedClientManager clients, AssessmentProperties properties) {
        return new AiGatewayGradingAdapter(
                client(builder, clients, properties.aiGatewayUrl(), properties.aiReadTimeout()));
    }

    private static RestClient client(
            RestClient.Builder builder, OAuth2AuthorizedClientManager clients, String baseUrl, Duration read) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(1000));
        factory.setReadTimeout(read);
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor(new ClientCredentialsInterceptor(clients, REGISTRATION))
                .build();
    }

    /** Taxonomy of docs/06 §6.3 and the synthetic anchors, from {@code classpath:grading/}. */
    @Bean
    GradingKit gradingKit(JsonMapper json) throws IOException {
        Map<String, Taxonomy.Entry> entries = new HashMap<>();
        for (JsonNode e : read(json, "grading/mistake-subcategories.json").path("entries")) {
            entries.put(
                    e.path("code").asString(),
                    new Taxonomy.Entry(
                            e.path("code").asString(),
                            e.path("description").asString(""),
                            e.path("unitCode").isNull()
                                    ? null
                                    : e.path("unitCode").asString(),
                            e.path("unitTitle").isNull()
                                    ? null
                                    : e.path("unitTitle").asString()));
        }
        Map<TaskType, List<GradingKit.Anchor>> anchors = new EnumMap<>(TaskType.class);
        for (JsonNode a : read(json, "grading/anchors.json").path("anchors")) {
            anchors.computeIfAbsent(TaskType.valueOf(a.path("taskType").asString()), k -> new ArrayList<>())
                    .add(new GradingKit.Anchor(
                            a.path("pass").asString(),
                            a.path("score").asInt(),
                            a.path("task").asString(),
                            a.path("text").asString(),
                            a.path("reason").asString()));
        }
        return new GradingKit(new Taxonomy(entries), anchors);
    }

    private static JsonNode read(JsonMapper json, String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return json.readTree(in);
        }
    }
}
