package com.toefly.tutoragent.application.port.out;

import com.toefly.tutoragent.domain.learner.MistakeRecord;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Vector memory of mistakes (docs/06 §6.4); the embedding is filled later by a job. */
public interface MistakeMemoryRepository {

    void enqueue(Collection<MistakeRecord> records);

    List<PendingMemory> pending(int limit);

    void embedded(UUID recordId, float[] embedding);

    void failed(UUID recordId);

    /** Nearest records of the learner by cosine similarity, without the given subcategories. */
    List<MemoryHit> nearest(UUID userId, float[] query, int topK, double minSimilarity, Collection<String> excluded);

    record PendingMemory(UUID recordId, UUID userId, String content) {}

    record MemoryHit(UUID recordId, String subcategory, String content, double similarity, Instant createdAt) {}
}
