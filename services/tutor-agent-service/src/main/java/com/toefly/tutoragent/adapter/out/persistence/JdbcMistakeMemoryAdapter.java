package com.toefly.tutoragent.adapter.out.persistence;

import com.toefly.tutoragent.application.port.out.MistakeMemoryRepository;
import com.toefly.tutoragent.domain.learner.MistakeRecord;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Vector memory in pgvector, cosine distance (docs/06 §6.4); written with plain SQL to filter by learner. */
@Component
public class JdbcMistakeMemoryAdapter implements MistakeMemoryRepository {

    static final int MAX_ATTEMPTS = 5;

    private final JdbcClient jdbc;

    public JdbcMistakeMemoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enqueue(Collection<MistakeRecord> records) {
        for (MistakeRecord r : records) {
            jdbc.sql("""
                            INSERT INTO mistake_memory (id, user_id, subcategory, content, created_at)
                            VALUES (:id, :u, :s, :c, :at) ON CONFLICT (id) DO NOTHING
                            """)
                    .param("id", r.id())
                    .param("u", r.userId())
                    .param("s", r.subcategory())
                    .param("c", r.memoryText())
                    .param("at", Timestamp.from(r.createdAt()))
                    .update();
        }
    }

    @Override
    public List<PendingMemory> pending(int limit) {
        return jdbc.sql("""
                        SELECT id, user_id, content FROM mistake_memory
                        WHERE embedding IS NULL AND attempts < :max ORDER BY created_at LIMIT :limit
                        """)
                .param("max", MAX_ATTEMPTS)
                .param("limit", limit)
                .query((rs, n) -> new PendingMemory(
                        rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("content")))
                .list();
    }

    @Override
    public void embedded(UUID recordId, float[] embedding) {
        jdbc.sql("UPDATE mistake_memory SET embedding = :e::vector WHERE id = :id")
                .param("e", literal(embedding))
                .param("id", recordId)
                .update();
    }

    @Override
    public void failed(UUID recordId) {
        jdbc.sql("UPDATE mistake_memory SET attempts = attempts + 1 WHERE id = :id")
                .param("id", recordId)
                .update();
    }

    @Override
    public List<MemoryHit> nearest(
            UUID userId, float[] query, int topK, double minSimilarity, Collection<String> excluded) {
        String[] skip = excluded.toArray(String[]::new);
        return jdbc
                .sql("""
                        SELECT id, subcategory, content, created_at, 1 - (embedding <=> :q::vector) AS similarity
                        FROM mistake_memory
                        WHERE user_id = :u AND embedding IS NOT NULL AND NOT (subcategory = ANY (:skip))
                        ORDER BY embedding <=> :q::vector LIMIT :k
                        """)
                .param("q", literal(query))
                .param("u", userId)
                .param("skip", skip)
                .param("k", topK)
                .query((rs, n) -> new MemoryHit(
                        rs.getObject("id", UUID.class),
                        rs.getString("subcategory"),
                        rs.getString("content"),
                        rs.getDouble("similarity"),
                        rs.getTimestamp("created_at").toInstant()))
                .list()
                .stream()
                .filter(hit -> hit.similarity() >= minSimilarity)
                .toList();
    }

    static String literal(float[] vector) {
        return IntStream.range(0, vector.length)
                .mapToObj(i -> String.format(Locale.ROOT, "%.6f", vector[i]))
                .collect(Collectors.joining(",", "[", "]"));
    }
}
