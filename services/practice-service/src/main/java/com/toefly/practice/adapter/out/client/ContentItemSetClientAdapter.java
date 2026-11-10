package com.toefly.practice.adapter.out.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.toefly.practice.application.port.out.ContentItemSetPort;
import com.toefly.practice.application.port.out.DependencyUnavailableException;
import com.toefly.practice.application.port.out.ItemSetUnavailableException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads item sets with keys from content-service with the service token of {@code svc-practice-service}; answers
 * are cached for 10 minutes (docs/04-architecture.md §3.6, ADR-0010).
 */
public class ContentItemSetClientAdapter implements ContentItemSetPort {

    static final Duration TTL = Duration.ofMinutes(10);

    private final RestClient content;
    private final Cache<UUID, String> cache =
            Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(2_000).build();

    public ContentItemSetClientAdapter(RestClient content) {
        this.content = content;
    }

    @Override
    public String fetch(UUID itemSetId) {
        return cache.get(itemSetId, this::load);
    }

    private String load(UUID itemSetId) {
        try {
            return Objects.requireNonNull(content.get()
                    .uri("/internal/v1/item-sets/{id}", itemSetId)
                    .retrieve()
                    .body(String.class));
        } catch (HttpClientErrorException error) {
            if (error.getStatusCode() == HttpStatus.NOT_FOUND || error.getStatusCode() == HttpStatus.CONFLICT) {
                throw new ItemSetUnavailableException(itemSetId);
            }
            throw new DependencyUnavailableException("content-service", error);
        } catch (RestClientException failure) {
            throw new DependencyUnavailableException("content-service", failure);
        }
    }
}
