package com.toefly.content.adapter.out.persistence;

import com.toefly.content.application.port.out.ExtractionStorePort;
import com.toefly.content.application.seed.Extraction;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcExtractionStoreAdapter implements ExtractionStorePort {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private final JdbcClient jdbc;

    public JdbcExtractionStoreAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(UUID targetId, String seedId, Extraction e) {
        jdbc.sql("""
                        INSERT INTO seed_extraction (target_id, seed_id, method, reviewed, warnings, updated_at)
                        VALUES (:id, :seed, :method, :reviewed, CAST(:warnings AS jsonb), :now)
                        ON CONFLICT (target_id) DO UPDATE SET seed_id = EXCLUDED.seed_id, method = EXCLUDED.method,
                            reviewed = EXCLUDED.reviewed, warnings = EXCLUDED.warnings, updated_at = EXCLUDED.updated_at
                        """)
                .param("id", targetId)
                .param("seed", seedId)
                .param("method", e.method())
                .param("reviewed", e.reviewed())
                .param("warnings", JSON.writeValueAsString(e.warnings()))
                .param("now", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public Map<UUID, Extraction> find(Collection<UUID> targetIds) {
        Map<UUID, Extraction> out = new HashMap<>();
        jdbc.sql("SELECT target_id, method, reviewed, warnings::text AS warnings FROM seed_extraction"
                        + " WHERE target_id IN (:ids)")
                .param("ids", targetIds)
                .query((rs, n) -> Map.entry(
                        rs.getObject("target_id", UUID.class),
                        new Extraction(
                                rs.getString("method"),
                                rs.getBoolean("reviewed"),
                                JSON.readValue(rs.getString("warnings"), STRINGS))))
                .list()
                .forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }
}
