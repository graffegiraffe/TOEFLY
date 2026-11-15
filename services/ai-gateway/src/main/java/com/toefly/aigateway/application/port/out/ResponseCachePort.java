package com.toefly.aigateway.application.port.out;

import java.util.Optional;

/** Answers of deterministic calls by the hash of their masked input (§2.6): Valkey, then PostgreSQL. */
public interface ResponseCachePort {

    Optional<CachedAnswer> get(String key);

    void put(String key, CachedAnswer answer);

    record CachedAnswer(String content, String provider, String model, String promptKey, int promptVersion) {}
}
