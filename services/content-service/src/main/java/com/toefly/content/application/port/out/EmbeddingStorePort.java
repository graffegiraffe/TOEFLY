package com.toefly.content.application.port.out;

import java.util.List;
import java.util.UUID;

public interface EmbeddingStorePort {

    record Pending(UUID itemSetId, String text) {}

    List<Pending> pending(int limit);

    void save(UUID itemSetId, int chunkNo, String content, float[] embedding);
}
