package com.toefly.vocabulary.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code toefly.vocabulary.*}: dependencies, AI switch and prompts, pronunciation flag, jobs. */
@ConfigurationProperties("toefly.vocabulary")
public record VocabularyProperties(
        @DefaultValue("http://localhost:8082") String contentUrl,
        @DefaultValue("http://localhost:8095") String aiGatewayUrl,
        @DefaultValue Ai ai,
        @DefaultValue("false") boolean pronunciationEnabled,
        @DefaultValue Jobs jobs) {

    /**
     * The interactive group of ai-gateway (docs/05 §9.3.8): 1 s to connect, 20 s to read. Prompt keys and use cases
     * of ai-gateway's registry.
     */
    public record Ai(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("20s") Duration readTimeout,
            @DefaultValue("TRANSLATION_RU") String wordCardUseCase,
            @DefaultValue("vocabulary.word-card") String wordCardPrompt,
            @DefaultValue("TRANSLATION_RU") String keyWordsUseCase,
            @DefaultValue("vocabulary.key-words") String keyWordsPrompt) {}

    public record Jobs(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("10m") Duration wordSetSync,
            @DefaultValue("2m") Duration enrichment,
            @DefaultValue("20") int enrichmentBatch,
            @DefaultValue("5") int maxEnrichAttempts) {}
}
