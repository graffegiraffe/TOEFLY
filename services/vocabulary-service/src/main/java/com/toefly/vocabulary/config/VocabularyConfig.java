package com.toefly.vocabulary.config;

import com.toefly.common.security.ClientCredentialsInterceptor;
import com.toefly.vocabulary.adapter.out.client.AiGatewayDictionaryAdapter;
import com.toefly.vocabulary.adapter.out.client.ContentCatalogAdapter;
import com.toefly.vocabulary.adapter.out.client.DisabledAiDictionaryAdapter;
import com.toefly.vocabulary.application.port.out.AiDictionaryPort;
import com.toefly.vocabulary.application.port.out.ContentCatalogPort;
import com.toefly.vocabulary.application.service.VocabularyPolicy;
import com.toefly.vocabulary.domain.fsrs.FsrsParameters;
import com.toefly.vocabulary.domain.fsrs.FsrsScheduler;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.client.RestClient;

/** Scheduler, policy and the clients of content-service and ai-gateway (service token of svc-vocabulary-service). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VocabularyProperties.class)
public class VocabularyConfig {

    static final String REGISTRATION = "svc-vocabulary-service";

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }

    /** fsrs-params-v1 (docs/03 §11.2). */
    @Bean
    FsrsScheduler fsrsScheduler() {
        return new FsrsScheduler(FsrsParameters.v1());
    }

    @Bean
    VocabularyPolicy vocabularyPolicy(VocabularyProperties properties) {
        return new VocabularyPolicy(
                properties.pronunciationEnabled(), properties.jobs().maxEnrichAttempts());
    }

    /** content-service: 500 ms to connect, 2 s to read (docs/05 §9.3). */
    @Bean
    ContentCatalogPort contentCatalog(
            RestClient.Builder builder, OAuth2AuthorizedClientManager clients, VocabularyProperties properties) {
        return new ContentCatalogAdapter(
                client(builder, clients, properties.contentUrl(), Duration.ofMillis(500), Duration.ofSeconds(2)));
    }

    @Bean
    AiDictionaryPort aiDictionary(
            RestClient.Builder builder, OAuth2AuthorizedClientManager clients, VocabularyProperties properties) {
        VocabularyProperties.Ai ai = properties.ai();
        if (!ai.enabled()) {
            return new DisabledAiDictionaryAdapter();
        }
        return new AiGatewayDictionaryAdapter(
                client(builder, clients, properties.aiGatewayUrl(), Duration.ofSeconds(1), ai.readTimeout()),
                new AiGatewayDictionaryAdapter.Prompts(
                        ai.wordCardUseCase(), ai.wordCardPrompt(), ai.keyWordsUseCase(), ai.keyWordsPrompt()));
    }

    private static RestClient client(
            RestClient.Builder builder,
            OAuth2AuthorizedClientManager clients,
            String baseUrl,
            Duration connect,
            Duration read) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor(new ClientCredentialsInterceptor(clients, REGISTRATION))
                .build();
    }
}
