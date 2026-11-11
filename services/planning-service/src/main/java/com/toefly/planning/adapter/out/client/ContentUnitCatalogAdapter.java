package com.toefly.planning.adapter.out.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.toefly.planning.application.port.out.DependencyUnavailableException;
import com.toefly.planning.application.port.out.UnitCatalogPort;
import com.toefly.planning.domain.composition.UnitRef;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Approved units of content-service in book order, read with the service token of {@code svc-planning-service}
 * and cached for 10 minutes (docs/12, phase 4, decision 10).
 */
public class ContentUnitCatalogAdapter implements UnitCatalogPort {

    static final Duration TTL = Duration.ofMinutes(10);

    private final RestClient content;
    private final Cache<String, List<UnitRef>> cache =
            Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(8).build();

    public ContentUnitCatalogAdapter(RestClient content) {
        this.content = content;
    }

    @Override
    public List<UnitRef> roundUp() {
        return cache.get("ROUND_UP_5", key -> load("/api/v1/grammar-units?framework=ROUND_UP_5", "Round-Up Unit "));
    }

    @Override
    public List<UnitRef> collinsGrammar() {
        return cache.get(
                "COLLINS_GRAMMAR",
                key -> load("/api/v1/grammar-units?framework=COLLINS_GRAMMAR", "Collins Grammar Unit "));
    }

    @Override
    public List<UnitRef> collinsVocabulary() {
        return cache.get("COLLINS_VOCABULARY", key -> load("/api/v1/vocabulary-units", "Collins Vocabulary Unit "));
    }

    private List<UnitRef> load(String uri, String prefix) {
        JsonNode body;
        try {
            body = content.get().uri(uri).retrieve().body(JsonNode.class);
        } catch (RestClientException failure) {
            throw new DependencyUnavailableException("content-service", failure);
        }
        List<JsonNode> items = new ArrayList<>();
        if (body != null) {
            body.path("items").forEach(items::add);
        }
        items.sort(Comparator.comparingInt(item -> item.path("unitNo").asInt()));
        return items.stream()
                .filter(item -> item.path("status").asString("APPROVED").equals("APPROVED"))
                .map(item -> new UnitRef(
                        UUID.fromString(item.path("id").asString()),
                        title(prefix + item.path("unitNo").asInt() + ": "
                                + item.path("title").asString())))
                .toList();
    }

    private static String title(String title) {
        return title.length() <= 200 ? title : title.substring(0, 199) + "…";
    }
}
