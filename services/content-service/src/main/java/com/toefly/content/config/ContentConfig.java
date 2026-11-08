package com.toefly.content.config;

import com.toefly.content.adapter.out.client.OllamaEmbeddingAdapter;
import com.toefly.content.adapter.out.seed.DirectorySeedLocationAdapter;
import com.toefly.content.adapter.out.seed.S3SeedLocationAdapter;
import com.toefly.content.application.port.out.EmbeddingPort;
import com.toefly.content.application.port.out.SeedLocationPort;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableScheduling
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
class ContentConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * {@code toefly.content.seed.location} is {@code dir:/path} (local development, tests) or {@code s3://bucket/prefix/}
     * (the private bucket {@code toefly-seed}; R2 in the cluster, SeaweedFS locally).
     */
    @Bean
    SeedLocationPort seedLocation(
            @Value("${toefly.content.seed.location:dir:./content/seed}") String location,
            @Value("${toefly.content.seed.s3.endpoint:http://localhost:9000}") String endpoint,
            @Value("${toefly.content.seed.s3.region:auto}") String region,
            @Value("${toefly.content.seed.s3.access-key:}") String accessKey,
            @Value("${toefly.content.seed.s3.secret-key:}") String secretKey) {
        if (location.startsWith("dir:")) {
            return new DirectorySeedLocationAdapter(Path.of(location.substring("dir:".length())));
        }
        if (location.startsWith("s3://")) {
            String[] bucketAndPrefix = S3SeedLocationAdapter.parse(location);
            return new S3SeedLocationAdapter(
                    s3(endpoint, region, accessKey, secretKey), bucketAndPrefix[0], bucketAndPrefix[1]);
        }
        throw new IllegalStateException("unsupported seed location: " + location);
    }

    static S3Client s3(String endpoint, String region, String accessKey, String secretKey) {
        AwsCredentialsProvider credentials = accessKey.isBlank()
                ? AnonymousCredentialsProvider.create()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        return S3Client.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto".equals(region) ? "us-east-1" : region))
                .forcePathStyle(true)
                .credentialsProvider(credentials)
                .build();
    }

    @Bean
    EmbeddingPort embeddings(
            @Value("${toefly.content.embeddings.url:http://localhost:11434}") String url,
            @Value("${toefly.content.embeddings.model:nomic-embed-text}") String model) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(2));
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));
        return new OllamaEmbeddingAdapter(
                RestClient.builder().baseUrl(url).requestFactory(factory).build(), model);
    }
}
