package com.toefly.aigateway.adapter.out.valkey;

import com.toefly.aigateway.application.port.out.ResponseCachePort;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cache of deterministic answers (§2.6): Valkey {@code aic:{hash}} for a week, then PostgreSQL
 * {@code ai_response_cache} without a TTL; a new prompt version gives a new key. Only masked answers are stored.
 */
public class TwoLevelResponseCache implements ResponseCachePort {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final StringRedisTemplate valkey;
    private final JdbcClient jdbc;
    private final Duration ttl;
    private final Clock clock;

    public TwoLevelResponseCache(StringRedisTemplate valkey, JdbcClient jdbc, Duration ttl, Clock clock) {
        this.valkey = valkey;
        this.jdbc = jdbc;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public Optional<CachedAnswer> get(String key) {
        try {
            String hot = valkey.opsForValue().get("aic:" + key);
            if (hot != null) {
                return Optional.of(JSON.readValue(hot, CachedAnswer.class));
            }
        } catch (RuntimeException valkeyDown) {
            // the second level still answers
        }
        Optional<CachedAnswer> cold = jdbc.sql("""
                        SELECT content, provider, model, prompt_key, prompt_version
                        FROM ai_response_cache WHERE cache_key = :key""")
                .param("key", key)
                .query((rs, n) -> new CachedAnswer(
                        rs.getString("content"),
                        rs.getString("provider"),
                        rs.getString("model"),
                        rs.getString("prompt_key"),
                        rs.getInt("prompt_version")))
                .optional();
        cold.ifPresent(answer -> warm(key, answer));
        return cold;
    }

    @Override
    public void put(String key, CachedAnswer answer) {
        jdbc.sql("""
                        INSERT INTO ai_response_cache
                            (cache_key, content, provider, model, prompt_key, prompt_version, created_at)
                        VALUES (:key, :content, :provider, :model, :promptKey, :promptVersion, :createdAt)
                        ON CONFLICT (cache_key) DO NOTHING""")
                .param("key", key)
                .param("content", answer.content())
                .param("provider", answer.provider())
                .param("model", answer.model())
                .param("promptKey", answer.promptKey())
                .param("promptVersion", answer.promptVersion())
                .param("createdAt", Timestamp.from(clock.instant()))
                .update();
        warm(key, answer);
    }

    private void warm(String key, CachedAnswer answer) {
        try {
            valkey.opsForValue().set("aic:" + key, JSON.writeValueAsString(answer), ttl);
        } catch (RuntimeException valkeyDown) {
            // PostgreSQL keeps the answer
        }
    }
}
