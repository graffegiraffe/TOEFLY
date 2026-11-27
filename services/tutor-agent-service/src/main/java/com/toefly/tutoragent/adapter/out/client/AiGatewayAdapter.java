package com.toefly.tutoragent.adapter.out.client;

import com.toefly.tutoragent.application.port.AiUnavailableException;
import com.toefly.tutoragent.application.port.out.AiPort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** {@code /internal/v1/ai/chat/completions} and {@code /internal/v1/ai/embeddings} of ai-gateway. */
public class AiGatewayAdapter implements AiPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient ai;

    public AiGatewayAdapter(RestClient ai) {
        this.ai = ai;
    }

    @Override
    public Completion complete(CompletionCall call) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("useCase", call.useCase());
        body.put("promptKey", call.promptKey());
        body.put("promptVersion", call.promptVersion());
        body.put("variables", call.variables());
        body.put("userId", call.userId());
        body.put("feature", call.feature());
        body.put("priority", call.priority());
        body.put("maxWaitMs", call.maxWaitMs());
        body.put("idempotencyKey", call.idempotencyKey());
        if (call.modelOverride() != null) {
            body.put("modelOverride", call.modelOverride());
        }
        JsonNode response = post("/internal/v1/ai/chat/completions", body);
        JsonNode stamp = response.path("aiStamp");
        JsonNode output = response.path("output");
        return new Completion(
                output.isObject() ? JSON.convertValue(output, new TypeReference<Map<String, Object>>() {}) : null,
                response.path("text").isString() ? response.path("text").asString() : null,
                new AiStamp(
                        stamp.path("aiCallId").asString(""),
                        stamp.path("promptKey").asString(null),
                        stamp.path("promptVersion").isInt()
                                ? stamp.path("promptVersion").asInt()
                                : null,
                        stamp.path("provider").asString(""),
                        stamp.path("model").asString(""),
                        stamp.path("degraded").asBoolean(false)),
                response.path("usage").path("inputTokens").asInt(0),
                response.path("usage").path("outputTokens").asInt(0));
    }

    @Override
    public List<float[]> embed(List<String> texts, @Nullable UUID userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("texts", texts);
        body.put("userId", userId);
        JsonNode response = post("/internal/v1/ai/embeddings", body);
        List<float[]> vectors = new ArrayList<>();
        for (JsonNode vector : response.path("vectors")) {
            float[] values = new float[vector.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = (float) vector.get(i).asDouble();
            }
            vectors.add(values);
        }
        if (vectors.size() != texts.size()) {
            throw new AiUnavailableException("ai-gateway returned " + vectors.size() + " vectors", null, false, null);
        }
        return vectors;
    }

    private JsonNode post(String path, Map<String, Object> body) {
        try {
            String raw = ai.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                throw new AiUnavailableException("ai-gateway returned an empty body", null, false, null);
            }
            return JSON.readTree(raw);
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            String retry = failure.getResponseHeaders() == null
                    ? null
                    : failure.getResponseHeaders().getFirst("Retry-After");
            throw new AiUnavailableException(
                    status + " from ai-gateway",
                    retry == null || !retry.matches("\\d+") ? null : Duration.ofSeconds(Long.parseLong(retry)),
                    status == 400 || status == 422,
                    failure);
        } catch (RestClientException | JacksonException failure) {
            throw new AiUnavailableException("ai-gateway unreachable: " + failure.getMessage(), null, false, failure);
        }
    }
}
