package com.toefly.content.adapter.out.client;

import com.toefly.content.application.port.out.EmbeddingPort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * Local embeddings through Ollama ({@code nomic-embed-text}, 768 dimensions). Calls go to Ollama directly because
 * {@code ai-gateway} does not exist before stage 5a; the texts are public item texts without personal data.
 */
public class OllamaEmbeddingAdapter implements EmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingAdapter.class);
    static final int DIMENSIONS = 768;

    private final RestClient http;
    private final String model;

    public OllamaEmbeddingAdapter(RestClient http, String model) {
        this.http = http;
        this.model = model;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<float[]> embed(String text) {
        try {
            Map<String, Object> response = http.post()
                    .uri("/api/embeddings")
                    .body(Map.of("model", model, "prompt", text))
                    .retrieve()
                    .body(Map.class);
            if (response == null
                    || !(response.get("embedding") instanceof List<?> numbers)
                    || numbers.size() != DIMENSIONS) {
                return Optional.empty();
            }
            float[] vector = new float[numbers.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = ((Number) numbers.get(i)).floatValue();
            }
            return Optional.of(vector);
        } catch (RuntimeException e) {
            log.debug("Embedding request failed: {}", e.toString());
            return Optional.empty();
        }
    }
}
