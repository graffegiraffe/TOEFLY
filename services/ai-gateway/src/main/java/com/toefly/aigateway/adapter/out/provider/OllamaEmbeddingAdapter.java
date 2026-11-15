package com.toefly.aigateway.adapter.out.provider;

import com.toefly.aigateway.application.port.out.EmbeddingPort;
import com.toefly.aigateway.domain.routing.ModelRef;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;

/** Local embeddings (nomic-embed-text, 768 dimensions) with Spring AI {@link OllamaEmbeddingModel}. */
public class OllamaEmbeddingAdapter implements EmbeddingPort {

    private final OllamaEmbeddingModel model;

    public OllamaEmbeddingAdapter(OllamaEmbeddingModel model) {
        this.model = model;
    }

    @Override
    public List<float[]> embed(ModelRef ref, List<String> texts) {
        try {
            EmbeddingResponse response = model.call(new EmbeddingRequest(
                    texts, OllamaEmbeddingOptions.builder().model(ref.model()).build()));
            return response.getResults().stream().map(e -> e.getOutput()).toList();
        } catch (RuntimeException error) {
            throw ProviderErrors.map("OLLAMA", error);
        }
    }
}
