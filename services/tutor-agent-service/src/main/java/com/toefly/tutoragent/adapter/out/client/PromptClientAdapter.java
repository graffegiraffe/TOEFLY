package com.toefly.tutoragent.adapter.out.client;

import com.toefly.tutoragent.application.port.AiUnavailableException;
import com.toefly.tutoragent.application.port.out.PromptPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Active prompt versions of ai-gateway, cached for a minute so an activation reaches the chat quickly. */
public class PromptClientAdapter implements PromptPort {

    static final Duration TTL = Duration.ofMinutes(1);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient ai;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(Prompt prompt, Instant at) {}

    public PromptClientAdapter(RestClient ai, Clock clock) {
        this.ai = ai;
        this.clock = clock;
    }

    @Override
    public Prompt active(String promptKey) {
        Cached cached = cache.get(promptKey);
        Instant now = clock.instant();
        if (cached != null && cached.at().plus(TTL).isAfter(now)) {
            return cached.prompt();
        }
        try {
            String raw = ai.get()
                    .uri("/internal/v1/prompts/{key}", promptKey)
                    .retrieve()
                    .body(String.class);
            JsonNode body = JSON.readTree(raw == null ? "{}" : raw);
            Prompt prompt = new Prompt(
                    promptKey,
                    body.path("version").asInt(1),
                    body.path("system").asString(""),
                    body.path("user").asString(""));
            cache.put(promptKey, new Cached(prompt, now));
            return prompt;
        } catch (RestClientException failure) {
            if (cached != null) {
                return cached.prompt();
            }
            throw new AiUnavailableException("prompt " + promptKey + " is unavailable", null, false, failure);
        }
    }
}
