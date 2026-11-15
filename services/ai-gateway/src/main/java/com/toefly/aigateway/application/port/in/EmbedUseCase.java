package com.toefly.aigateway.application.port.in;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@code POST /internal/v1/ai/embeddings}: local model only. */
public interface EmbedUseCase {

    Embeddings embed(String callerService, List<String> texts, @Nullable UUID userId);

    record Embeddings(List<float[]> vectors, int dimensions, String model) {}
}
