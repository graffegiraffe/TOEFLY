package com.toefly.aigateway.config;

import com.toefly.aigateway.adapter.out.azure.AzureSpeechAdapter;
import com.toefly.aigateway.adapter.out.messaging.OutboxUsageEventPublisherAdapter;
import com.toefly.aigateway.adapter.out.persistence.JdbcAiCallRepositoryAdapter;
import com.toefly.aigateway.adapter.out.persistence.JdbcEvalRunAdapter;
import com.toefly.aigateway.adapter.out.persistence.JdbcPromptRepositoryAdapter;
import com.toefly.aigateway.adapter.out.persistence.JdbcProviderQuotaAdapter;
import com.toefly.aigateway.adapter.out.provider.GroqTranscriptionAdapter;
import com.toefly.aigateway.adapter.out.provider.HttpAudioSourceAdapter;
import com.toefly.aigateway.adapter.out.provider.JtokkitTokenCounter;
import com.toefly.aigateway.adapter.out.provider.OllamaChatAdapter;
import com.toefly.aigateway.adapter.out.provider.OllamaEmbeddingAdapter;
import com.toefly.aigateway.adapter.out.provider.OllamaGuardAdapter;
import com.toefly.aigateway.adapter.out.provider.OllamaNerAdapter;
import com.toefly.aigateway.adapter.out.provider.OpenAiCompatibleChatAdapter;
import com.toefly.aigateway.adapter.out.resilience.Resilience4jAdapter;
import com.toefly.aigateway.adapter.out.schema.JsonOutputSchemaAdapter;
import com.toefly.aigateway.adapter.out.template.PromptFileSource;
import com.toefly.aigateway.adapter.out.template.RoutesFileAdapter;
import com.toefly.aigateway.adapter.out.template.StPromptRenderer;
import com.toefly.aigateway.adapter.out.valkey.EncryptedConversationMasking;
import com.toefly.aigateway.adapter.out.valkey.TwoLevelResponseCache;
import com.toefly.aigateway.adapter.out.valkey.ValkeyQuotaStore;
import com.toefly.aigateway.application.port.out.PriceListPort;
import com.toefly.aigateway.application.service.GatewaySettings;
import com.toefly.aigateway.domain.call.ModelPrice;
import com.toefly.aigateway.domain.quota.ProviderQuota;
import com.toefly.aigateway.domain.quota.QuotaDimension;
import com.toefly.aigateway.domain.quota.QuotaLimit;
import com.toefly.aigateway.domain.quota.ReservePolicy;
import com.toefly.aigateway.domain.routing.AiProvider;
import com.toefly.aigateway.domain.routing.ModelRef;
import com.toefly.common.outbox.IntegrationEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

/** Wires the adapters of ai-gateway from {@code toefly.ai.*}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiGatewayProperties.class)
class AiGatewayConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    GatewaySettings gatewaySettings(AiGatewayProperties properties) {
        return new GatewaySettings(
                new ReservePolicy(
                        properties.quotas().reserveFloor().grading(),
                        properties.quotas().reserveFloor().background()),
                properties.quotas().maxQueuedGradingPerUser(),
                2,
                properties.pii().nerEnabled(),
                properties.prompts().requireEvals(),
                properties.prompts().activateOnSync(),
                properties.callLog().payloadRetention(),
                properties.callLog().rowRetention(),
                properties.resilience().retryJitter());
    }

    @Bean
    RoutesFileAdapter routesFileAdapter(AiGatewayProperties properties) {
        return new RoutesFileAdapter(properties.routesConfig());
    }

    @Bean
    OpenAiCompatibleChatAdapter groqChatAdapter(AiGatewayProperties properties) {
        return new OpenAiCompatibleChatAdapter(
                AiProvider.GROQ,
                "GROQ_API_KEY",
                properties.groq().openAiBaseUrl(),
                properties.groq().apiKey(),
                properties.groq().timeout());
    }

    @Bean
    OpenAiCompatibleChatAdapter zaiChatAdapter(AiGatewayProperties properties) {
        return new OpenAiCompatibleChatAdapter(
                AiProvider.ZAI,
                "ZAI_API_KEY",
                properties.zai().baseUrl(),
                properties.zai().apiKey(),
                properties.zai().timeout());
    }

    @Bean
    OpenAiCompatibleChatAdapter openRouterChatAdapter(AiGatewayProperties properties) {
        return new OpenAiCompatibleChatAdapter(
                AiProvider.OPENROUTER,
                "OPENROUTER_API_KEY",
                properties.openRouter().baseUrl(),
                properties.openRouter().apiKey(),
                properties.openRouter().timeout());
    }

    @Bean
    OllamaApi ollamaApi(AiGatewayProperties properties) {
        // Ollama speaks HTTP/1.1; the default h2c upgrade of the JDK client is refused by some proxies.
        java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
        return OllamaApi.builder()
                .baseUrl(properties.ollama().baseUrl())
                .restClientBuilder(RestClient.builder()
                        .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(http)))
                .build();
    }

    @Bean
    OllamaChatModel ollamaChatModel(OllamaApi api) {
        return OllamaChatModel.builder()
                .ollamaApi(api)
                .options(OllamaChatOptions.builder().model("llama3.2:3b").build())
                .retryTemplate(
                        new RetryTemplate(RetryPolicy.builder().maxRetries(0).build()))
                .build();
    }

    @Bean
    OllamaChatAdapter ollamaChatAdapter(OllamaChatModel model) {
        return new OllamaChatAdapter(model);
    }

    @Bean
    OllamaEmbeddingAdapter ollamaEmbeddingAdapter(OllamaApi api) {
        return new OllamaEmbeddingAdapter(OllamaEmbeddingModel.builder()
                .ollamaApi(api)
                .options(OllamaEmbeddingOptions.builder()
                        .model("nomic-embed-text")
                        .build())
                .build());
    }

    @Bean
    OllamaGuardAdapter ollamaGuardAdapter(AiGatewayProperties properties) {
        return new OllamaGuardAdapter(
                RestClient.builder()
                        .baseUrl(properties.ollama().baseUrl())
                        .requestFactory(timeouts(5_000, 30_000))
                        .build(),
                properties.guard().model());
    }

    @Bean
    OllamaNerAdapter ollamaNerAdapter(OllamaChatModel model, AiGatewayProperties properties) {
        return new OllamaNerAdapter(model, properties.pii().nerModel());
    }

    @Bean
    GroqTranscriptionAdapter groqTranscriptionAdapter(AiGatewayProperties properties) {
        return new GroqTranscriptionAdapter(
                RestClient.builder()
                        .baseUrl(properties.groq().baseUrl())
                        .requestFactory(timeouts(
                                5_000, (int) properties.groq().timeout().toMillis()))
                        .build(),
                properties.groq().apiKey());
    }

    @Bean
    AzureSpeechAdapter azureSpeechAdapter(AiGatewayProperties properties) {
        SimpleClientHttpRequestFactory factory = timeouts(5_000, 60_000);
        return new AzureSpeechAdapter(
                RestClient.builder()
                        .baseUrl(properties.azureSpeech().stt())
                        .requestFactory(factory)
                        .build(),
                RestClient.builder()
                        .baseUrl(properties.azureSpeech().tts())
                        .requestFactory(factory)
                        .build(),
                properties.azureSpeech().key());
    }

    @Bean
    HttpAudioSourceAdapter httpAudioSourceAdapter() {
        return new HttpAudioSourceAdapter(
                RestClient.builder().requestFactory(timeouts(5_000, 30_000)).build());
    }

    @Bean
    JtokkitTokenCounter tokenCounter() {
        return new JtokkitTokenCounter();
    }

    @Bean
    StPromptRenderer promptRenderer() {
        return new StPromptRenderer();
    }

    @Bean
    JsonOutputSchemaAdapter outputSchemaAdapter() {
        return new JsonOutputSchemaAdapter();
    }

    @Bean
    PromptFileSource promptFileSource(AiGatewayProperties properties, Clock clock) {
        return new PromptFileSource(properties.prompts().location(), clock);
    }

    @Bean
    ValkeyQuotaStore valkeyQuotaStore(StringRedisTemplate valkey) {
        return new ValkeyQuotaStore(valkey);
    }

    @Bean
    TwoLevelResponseCache responseCache(
            StringRedisTemplate valkey, JdbcClient jdbc, AiGatewayProperties properties, Clock clock) {
        return new TwoLevelResponseCache(valkey, jdbc, properties.cache().valkeyTtl(), clock);
    }

    @Bean
    EncryptedConversationMasking conversationMasking(StringRedisTemplate valkey, AiGatewayProperties properties) {
        return new EncryptedConversationMasking(
                valkey, key(properties.pii().sessionKey()), properties.pii().sessionTtl());
    }

    @Bean
    JdbcPromptRepositoryAdapter promptRepository(JdbcClient jdbc) {
        return new JdbcPromptRepositoryAdapter(jdbc);
    }

    @Bean
    JdbcAiCallRepositoryAdapter aiCallRepository(JdbcClient jdbc) {
        return new JdbcAiCallRepositoryAdapter(jdbc);
    }

    @Bean
    JdbcEvalRunAdapter evalRunRepository(JdbcClient jdbc) {
        return new JdbcEvalRunAdapter(jdbc);
    }

    @Bean
    JdbcProviderQuotaAdapter providerQuotaRepository(JdbcClient jdbc) {
        return new JdbcProviderQuotaAdapter(jdbc);
    }

    @Bean
    OutboxUsageEventPublisherAdapter usageEventPublisher(IntegrationEventPublisher publisher) {
        return new OutboxUsageEventPublisherAdapter(publisher);
    }

    @Bean
    Resilience4jAdapter resilience(MeterRegistry meters, AiGatewayProperties properties) {
        return new Resilience4jAdapter(
                meters,
                properties.resilience().concurrency(),
                properties.resilience().openFor());
    }

    @Bean
    PriceListPort priceList(AiGatewayProperties properties) {
        Map<ModelRef, ModelPrice> prices = new HashMap<>();
        for (AiGatewayProperties.Price price : properties.prices()) {
            prices.put(
                    ModelRef.parse(price.model()),
                    new ModelPrice(
                            price.inputPerMillion(),
                            price.outputPerMillion(),
                            price.perAudioHour(),
                            price.perMillionChars()));
        }
        return model -> prices.getOrDefault(model, ModelPrice.FREE);
    }

    static List<ProviderQuota> defaultQuotas(AiGatewayProperties properties) {
        return properties.quotas().models().stream()
                .map(model -> new ProviderQuota(
                        ModelRef.parse(model.model()),
                        model.limits().entrySet().stream()
                                .map(e -> new QuotaLimit(QuotaDimension.valueOf(e.getKey()), e.getValue()))
                                .toList()))
                .toList();
    }

    /** A 32-byte AES key from base64; without one (local runs only) a fixed development key is derived. */
    static byte[] key(String base64) {
        if (!base64.isBlank()) {
            return Base64.getDecoder().decode(base64);
        }
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest("toefly-local-masking-key".getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static SimpleClientHttpRequestFactory timeouts(int connectMs, int readMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readMs);
        return factory;
    }
}
