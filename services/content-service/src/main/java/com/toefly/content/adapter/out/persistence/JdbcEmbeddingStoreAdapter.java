package com.toefly.content.adapter.out.persistence;

import com.toefly.content.application.port.out.EmbeddingStorePort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEmbeddingStoreAdapter implements EmbeddingStorePort {

    private final JdbcClient jdbc;

    public JdbcEmbeddingStoreAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Pending> pending(int limit) {
        return jdbc.sql("""
                        SELECT i.id, i.topic || ' ' || i.search_text AS text FROM item_set i
                        WHERE i.status = 'APPROVED'
                          AND NOT EXISTS (SELECT 1 FROM item_set_embedding e WHERE e.item_set_id = i.id)
                        ORDER BY i.created_at LIMIT :limit
                        """)
                .param("limit", limit)
                .query((rs, n) -> new Pending(rs.getObject("id", UUID.class), rs.getString("text")))
                .list();
    }

    @Override
    public void save(UUID itemSetId, int chunkNo, String content, float[] embedding) {
        jdbc.sql("""
                        INSERT INTO item_set_embedding (item_set_id, chunk_no, content, embedding)
                        VALUES (:id, :chunk, :content, CAST(:v AS vector))
                        ON CONFLICT (item_set_id, chunk_no) DO UPDATE SET content = EXCLUDED.content,
                            embedding = EXCLUDED.embedding
                        """)
                .param("id", itemSetId)
                .param("chunk", chunkNo)
                .param("content", content)
                .param("v", JdbcCatalogQueryAdapter.vector(embedding))
                .update();
    }
}
