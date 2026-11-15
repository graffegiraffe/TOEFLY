package com.toefly.aigateway.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code toefly.ai.*} (docs/06-ai-agent.md §11). */
@ConfigurationProperties("toefly.ai")
public record AiGatewayProperties(
        @Nullable String routesConfig,
        @DefaultValue Groq groq,
        @DefaultValue Ollama ollama,
        @DefaultValue AzureSpeech azureSpeech,
        @DefaultValue Quotas quotas,
        @DefaultValue Guard guard,
        @DefaultValue Pii pii,
        @DefaultValue Cache cache,
        @DefaultValue CallLog callLog,
        @DefaultValue Prompts prompts,
        @DefaultValue Resilience resilience,
        @Nullable List<Price> prices) {

    public AiGatewayProperties {
        prices = prices == null ? List.of() : List.copyOf(prices);
    }

    public record Groq(
            @DefaultValue("https://api.groq.com") String baseUrl,
            @DefaultValue("") String apiKey,
            @DefaultValue("PT60S") Duration timeout) {

        /** Spring AI and the OpenAI SDK need the path of the OpenAI-compatible API. */
        public String openAiBaseUrl() {
            return baseUrl.endsWith("/") ? baseUrl + "openai/v1" : baseUrl + "/openai/v1";
        }
    }

    public record Ollama(
            @DefaultValue("http://localhost:11434") String baseUrl) {}

    public record AzureSpeech(
            @DefaultValue("") String key,
            @DefaultValue("westeurope") String region,
            @Nullable String sttBaseUrl,
            @Nullable String ttsBaseUrl) {

        public String stt() {
            return sttBaseUrl != null ? sttBaseUrl : "https://" + region + ".stt.speech.microsoft.com";
        }

        public String tts() {
            return ttsBaseUrl != null ? ttsBaseUrl : "https://" + region + ".tts.speech.microsoft.com";
        }
    }

    public record Quotas(
            @DefaultValue ReserveFloor reserveFloor,
            @DefaultValue("2") int maxQueuedGradingPerUser,
            @Nullable List<ModelLimits> models) {

        public Quotas {
            models = models == null ? List.of() : List.copyOf(models);
        }
    }

    public record ReserveFloor(
            @DefaultValue("0.10") double grading,
            @DefaultValue("0.30") double background) {}

    /** Free-tier limits of one model, e.g. {@code model: GROQ:openai/gpt-oss-120b, limits: {RPM: 30}}. */
    public record ModelLimits(String model, Map<String, Long> limits) {}

    public record Guard(@DefaultValue("llama-guard3:1b") String model) {}

    public record Pii(
            @DefaultValue("true") boolean nerEnabled,
            @DefaultValue("qwen2.5:1.5b") String nerModel,
            @DefaultValue("PT24H") Duration sessionTtl,
            @DefaultValue("") String sessionKey) {}

    public record Cache(@DefaultValue("P7D") Duration valkeyTtl) {}

    public record CallLog(
            @DefaultValue("P30D") Duration payloadRetention,
            @DefaultValue("P90D") Duration rowRetention) {}

    public record Prompts(
            @DefaultValue("classpath:prompts") String location,
            @DefaultValue("true") boolean requireEvals,
            @DefaultValue("false") boolean activateOnSync) {}

    public record Resilience(
            @DefaultValue("PT60S") Duration openFor,
            @DefaultValue("PT0.3S") Duration retryJitter,
            @Nullable Map<String, Integer> concurrency) {

        public Resilience {
            concurrency = concurrency == null ? Map.of() : Map.copyOf(concurrency);
        }
    }

    /** Paid price of a model in US dollars (§10). */
    public record Price(
            String model,
            @DefaultValue("0") double inputPerMillion,
            @DefaultValue("0") double outputPerMillion,
            @DefaultValue("0") double perAudioHour,
            @DefaultValue("0") double perMillionChars) {}
}
