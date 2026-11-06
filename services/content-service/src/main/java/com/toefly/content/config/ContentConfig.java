package com.toefly.content.config;

import com.toefly.content.adapter.out.seed.DirectorySeedLocationAdapter;
import com.toefly.content.application.port.out.SeedLocationPort;
import com.toefly.content.adapter.out.client.OllamaEmbeddingAdapter;
import com.toefly.content.application.port.out.EmbeddingPort;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
class ContentConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** {@code toefly.content.seed.location} is {@code dir:/path}; the S3 reader of the private bucket follows. */
    @Bean
    SeedLocationPort seedLocation(@Value("${toefly.content.seed.location:dir:./content/seed}") String location) {
        if (!location.startsWith("dir:")) {
            throw new IllegalStateException("unsupported seed location: " + location);
        }
        return new DirectorySeedLocationAdapter(Path.of(location.substring("dir:".length())));
    }

    @Bean
    EmbeddingPort embeddings(
            @Value("${toefly.content.embeddings.url:http://localhost:11434}") String url,
            @Value("${toefly.content.embeddings.model:nomic-embed-text}") String model) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(2));
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));
        return new OllamaEmbeddingAdapter(RestClient.builder().baseUrl(url).requestFactory(factory).build(), model);
    }
}
