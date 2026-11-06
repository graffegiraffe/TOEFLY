package com.toefly.content.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface SeedImportStorePort {

    /** Takes the advisory lock of the import for the current transaction or returns false if someone holds it. */
    boolean tryLock();

    Optional<String> bundleSha(String sourceCode);

    void record(String sourceCode, String bundleSha, String status, Map<String, Integer> counts, String reportJson,
            Instant startedAt, Instant finishedAt);
}
