package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.routing.ModelRef;
import java.util.List;

/** Local embeddings (Ollama); throws {@link ProviderFailure}. */
public interface EmbeddingPort {

    List<float[]> embed(ModelRef model, List<String> texts);
}
