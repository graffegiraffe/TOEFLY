package com.toefly.tutoragent.adapter.out.client;

import com.toefly.tutoragent.application.port.out.OwnerToolsPort;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Internal APIs of planning-, analytics- and content-service used by the agent's tools (docs/05 §9.3). */
public class OwnerToolsAdapter implements OwnerToolsPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient planning;
    private final RestClient analytics;
    private final RestClient content;

    public OwnerToolsAdapter(RestClient planning, RestClient analytics, RestClient content) {
        this.planning = planning;
        this.analytics = analytics;
        this.content = content;
    }

    @Override
    public Map<String, Object> currentWeek(UUID userId, @Nullable String bearer) {
        return map(
                OwnerCalls.get(planning, "planning-service", "/internal/v1/users/{id}/weeks/current", bearer, userId));
    }

    @Override
    public Map<String, Object> addExtraTask(UUID weekId, Map<String, Object> request, String idempotencyKey) {
        try {
            String raw = planning.post()
                    .uri("/internal/v1/weeks/{id}/tasks", weekId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            return map(JSON.readTree(raw == null ? "{}" : raw));
        } catch (RestClientResponseException refused) {
            int status = refused.getStatusCode().value();
            if (status == 409 || status == 422) {
                throw new com.toefly.tutoragent.application.port.OwnerRefusedException(
                        problem(refused.getResponseBodyAsString()));
            }
            throw new com.toefly.tutoragent.application.port.DependencyUnavailableException(
                    "planning-service", "planning-service answered " + status, refused);
        } catch (RestClientException failure) {
            throw new com.toefly.tutoragent.application.port.DependencyUnavailableException(
                    "planning-service", "planning-service is unreachable", failure);
        }
    }

    @Override
    public Map<String, Object> dashboard(UUID userId, @Nullable String bearer) {
        return map(OwnerCalls.get(analytics, "analytics-service", "/internal/v1/users/{id}/dashboard", bearer, userId));
    }

    @Override
    public Map<String, Object> timeReport(UUID userId, String period, @Nullable String bearer) {
        return map(OwnerCalls.get(
                analytics,
                "analytics-service",
                "/internal/v1/users/{id}/time-reports?period={p}",
                bearer,
                userId,
                period));
    }

    @Override
    public Map<String, Object> searchItemSets(Map<String, Object> query) {
        return map(OwnerCalls.post(content, "content-service", "/internal/v1/item-sets/search", query, null));
    }

    @Override
    public Map<String, Object> searchGrammarUnits(@Nullable String topic, @Nullable String subcategory, int limit) {
        return map(OwnerCalls.get(
                content,
                "content-service",
                "/internal/v1/grammar-units/search?topic={t}&subcategory={s}&limit={l}",
                null,
                topic == null ? "" : topic,
                subcategory == null ? "" : subcategory,
                limit));
    }

    private static Map<String, Object> map(JsonNode node) {
        if (node == null || !node.isObject()) {
            return new LinkedHashMap<>();
        }
        return JSON.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }

    private static String problem(String body) {
        try {
            JsonNode node = JSON.readTree(body);
            return node.path("detail").asString(node.path("title").asString("The week cannot take this task."));
        } catch (RuntimeException unreadable) {
            return "The week cannot take this task.";
        }
    }

    static HttpHeaders none() {
        return new HttpHeaders();
    }
}
