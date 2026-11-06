package com.toefly.content.adapter.out.persistence;

import com.toefly.content.application.port.out.SeedImportStorePort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSeedImportStoreAdapter implements SeedImportStorePort {

    private static final long LOCK_KEY = "seed-import".hashCode();

    private final JdbcClient jdbc;

    public JdbcSeedImportStoreAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tryLock() {
        return Boolean.TRUE.equals(jdbc.sql("SELECT pg_try_advisory_xact_lock(:k)")
                .param("k", LOCK_KEY)
                .query(Boolean.class)
                .single());
    }

    @Override
    public Optional<String> bundleSha(String sourceCode) {
        return jdbc.sql("SELECT bundle_sha256 FROM seed_import WHERE source_code = :c AND status = 'DONE'")
                .param("c", sourceCode)
                .query(String.class)
                .optional();
    }

    @Override
    public void record(
            String sourceCode,
            String bundleSha,
            String status,
            Map<String, Integer> counts,
            String reportJson,
            Instant startedAt,
            Instant finishedAt) {
        String countsJson = counts.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                .collect(Collectors.joining(",", "{", "}"));
        jdbc.sql("""
                        INSERT INTO seed_import (source_code, bundle_sha256, status, counts, report, started_at, finished_at)
                        VALUES (:c, :sha, :status, CAST(:counts AS jsonb), CAST(:report AS jsonb), :s, :f)
                        ON CONFLICT (source_code) DO UPDATE SET bundle_sha256 = EXCLUDED.bundle_sha256,
                            status = EXCLUDED.status, counts = EXCLUDED.counts, report = EXCLUDED.report,
                            started_at = EXCLUDED.started_at, finished_at = EXCLUDED.finished_at
                        """)
                .param("c", sourceCode)
                .param("sha", bundleSha)
                .param("status", status)
                .param("counts", countsJson)
                .param("report", reportJson)
                .param("s", Timestamp.from(startedAt))
                .param("f", Timestamp.from(finishedAt))
                .update();
    }
}
