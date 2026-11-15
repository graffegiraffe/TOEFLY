package com.toefly.aigateway.adapter.out.persistence;

import com.toefly.aigateway.application.port.out.AiCallRepository;
import com.toefly.aigateway.domain.call.AiCall;
import com.toefly.aigateway.domain.call.AiCallOutcome;
import com.toefly.aigateway.domain.call.CallCursor;
import com.toefly.aigateway.domain.call.CallFilter;
import com.toefly.aigateway.domain.call.TokenUsage;
import com.toefly.aigateway.domain.call.UsageRow;
import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.routing.AiProvider;
import com.toefly.aigateway.domain.routing.AiUseCase;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The call journal {@code ai_call} and the daily aggregates {@code ai_usage_daily}, written together. */
public class JdbcAiCallRepositoryAdapter implements AiCallRepository {

    private final JdbcClient jdbc;

    public JdbcAiCallRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(AiCall call) {
        jdbc.sql("""
                        INSERT INTO ai_call (id, caller_service, use_case, feature, priority, user_id, prompt_key,
                            prompt_version, provider, model, fallback_depth, degraded, cache_hit, request_masked,
                            response_masked, input_tokens, output_tokens, audio_seconds, characters, latency_ms,
                            shadow_cost_micro_usd, guard_verdict, outcome, error_code, trace_id, correlation_id,
                            created_at)
                        VALUES (:id, :caller, :useCase, :feature, :priority, :userId, :promptKey, :promptVersion,
                            :provider, :model, :depth, :degraded, :cacheHit, CAST(:request AS jsonb), :response,
                            :input, :output, :audio, :characters, :latency, :cost, :verdict, :outcome, :error,
                            :traceId, :correlationId, :createdAt)""")
                .param("id", call.id())
                .param("caller", call.callerService())
                .param("useCase", call.useCase().name())
                .param("feature", call.feature())
                .param(
                        "priority",
                        call.priority() == null ? null : call.priority().name())
                .param("userId", call.userId())
                .param("promptKey", call.promptKey())
                .param("promptVersion", call.promptVersion())
                .param("provider", call.provider().name())
                .param("model", call.model())
                .param("depth", call.fallbackDepth())
                .param("degraded", call.degraded())
                .param("cacheHit", call.cacheHit())
                .param("request", call.requestMasked())
                .param("response", call.responseMasked())
                .param("input", call.usage().inputTokens())
                .param("output", call.usage().outputTokens())
                .param("audio", call.usage().audioSeconds())
                .param("characters", call.usage().characters())
                .param("latency", call.latencyMs())
                .param("cost", call.shadowCostMicroUsd())
                .param("verdict", call.guardVerdict())
                .param("outcome", call.outcome().name())
                .param("error", call.errorCode())
                .param("traceId", call.traceId())
                .param("correlationId", call.correlationId())
                .param("createdAt", Timestamp.from(call.createdAt()))
                .update();
        jdbc.sql("""
                        INSERT INTO ai_usage_daily (day, user_key, feature, provider, model, use_case, calls,
                            cache_hits, fallbacks, input_tokens, output_tokens, audio_seconds, characters,
                            shadow_cost_micro_usd)
                        VALUES (:day, :userKey, :feature, :provider, :model, :useCase, 1, :cacheHit, :fallback,
                            :input, :output, :audio, :characters, :cost)
                        ON CONFLICT (day, user_key, feature, provider, model, use_case) DO UPDATE SET
                            calls = ai_usage_daily.calls + 1,
                            cache_hits = ai_usage_daily.cache_hits + EXCLUDED.cache_hits,
                            fallbacks = ai_usage_daily.fallbacks + EXCLUDED.fallbacks,
                            input_tokens = ai_usage_daily.input_tokens + EXCLUDED.input_tokens,
                            output_tokens = ai_usage_daily.output_tokens + EXCLUDED.output_tokens,
                            audio_seconds = ai_usage_daily.audio_seconds + EXCLUDED.audio_seconds,
                            characters = ai_usage_daily.characters + EXCLUDED.characters,
                            shadow_cost_micro_usd = ai_usage_daily.shadow_cost_micro_usd
                                + EXCLUDED.shadow_cost_micro_usd""")
                .param("day", Date.valueOf(LocalDate.ofInstant(call.createdAt(), ZoneOffset.UTC)))
                .param("userKey", call.userId() == null ? "" : call.userId().toString())
                .param("feature", call.feature() == null ? "" : call.feature())
                .param("provider", call.provider().name())
                .param("model", call.model())
                .param("useCase", call.useCase().name())
                .param("cacheHit", call.cacheHit() ? 1 : 0)
                .param("fallback", call.fallbackDepth() > 0 ? 1 : 0)
                .param("input", call.usage().inputTokens())
                .param("output", call.usage().outputTokens())
                .param("audio", call.usage().audioSeconds())
                .param("characters", call.usage().characters())
                .param("cost", call.shadowCostMicroUsd())
                .update();
    }

    @Override
    public List<AiCall> page(CallFilter filter, @Nullable CallCursor after, int limit) {
        return jdbc.sql("""
                        SELECT * FROM ai_call
                        WHERE (CAST(:promptKey AS varchar) IS NULL OR prompt_key = :promptKey)
                          AND (CAST(:provider AS varchar) IS NULL OR provider = :provider)
                          AND (CAST(:outcome AS varchar) IS NULL OR outcome = :outcome)
                          AND (CAST(:from AS timestamptz) IS NULL OR created_at >= :from)
                          AND (CAST(:to AS timestamptz) IS NULL OR created_at < :to)
                          AND (CAST(:afterAt AS timestamptz) IS NULL OR (created_at, id) < (:afterAt, :afterId))
                        ORDER BY created_at DESC, id DESC
                        LIMIT :limit""")
                .param("promptKey", filter.promptKey())
                .param("provider", filter.provider())
                .param("outcome", filter.outcome())
                .param("from", timestamp(filter.from()))
                .param("to", timestamp(filter.to()))
                .param("afterAt", after == null ? null : Timestamp.from(after.createdAt()))
                .param("afterId", after == null ? "" : after.id())
                .param("limit", limit)
                .query(JdbcAiCallRepositoryAdapter::call)
                .list();
    }

    @Override
    public List<UsageRow> usage(@Nullable UUID userId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT day, provider, model, use_case, NULLIF(feature, '') AS feature,
                            SUM(calls) AS calls, SUM(cache_hits) AS cache_hits, SUM(fallbacks) AS fallbacks,
                            SUM(input_tokens) AS input_tokens, SUM(output_tokens) AS output_tokens,
                            SUM(audio_seconds) AS audio_seconds, SUM(characters) AS characters,
                            SUM(shadow_cost_micro_usd) AS cost
                        FROM ai_usage_daily
                        WHERE day BETWEEN :from AND :to
                          AND (CAST(:userKey AS varchar) IS NULL OR user_key = :userKey)
                        GROUP BY day, provider, model, use_case, feature
                        ORDER BY day, provider, model, use_case""")
                .param("from", Date.valueOf(from))
                .param("to", Date.valueOf(to))
                .param("userKey", userId == null ? null : userId.toString())
                .query((rs, n) -> new UsageRow(
                        rs.getDate("day").toLocalDate(),
                        rs.getString("provider"),
                        rs.getString("model"),
                        rs.getString("use_case"),
                        rs.getString("feature"),
                        rs.getLong("calls"),
                        rs.getLong("cache_hits"),
                        rs.getLong("fallbacks"),
                        rs.getLong("input_tokens"),
                        rs.getLong("output_tokens"),
                        rs.getDouble("audio_seconds"),
                        rs.getLong("characters"),
                        rs.getLong("cost")))
                .list();
    }

    @Override
    public int deleteUser(UUID userId) {
        int calls = jdbc.sql("DELETE FROM ai_call WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        jdbc.sql("DELETE FROM ai_usage_daily WHERE user_key = :userKey")
                .param("userKey", userId.toString())
                .update();
        return calls;
    }

    @Override
    public int clearPayloads(Instant before) {
        return jdbc.sql("""
                        UPDATE ai_call SET request_masked = NULL, response_masked = NULL
                        WHERE created_at < :before AND (request_masked IS NOT NULL OR response_masked IS NOT NULL)""").param("before", Timestamp.from(before)).update();
    }

    @Override
    public int deleteOlderThan(Instant before) {
        return jdbc.sql("DELETE FROM ai_call WHERE created_at < :before")
                .param("before", Timestamp.from(before))
                .update();
    }

    private static @Nullable Timestamp timestamp(@Nullable Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static AiCall call(ResultSet rs, int row) throws SQLException {
        String priority = rs.getString("priority");
        return new AiCall(
                rs.getString("id"),
                rs.getString("caller_service"),
                AiUseCase.valueOf(rs.getString("use_case")),
                rs.getString("feature"),
                priority == null ? null : Priority.valueOf(priority),
                rs.getObject("user_id", UUID.class),
                rs.getString("prompt_key"),
                (Integer) rs.getObject("prompt_version"),
                AiProvider.valueOf(rs.getString("provider")),
                rs.getString("model"),
                rs.getInt("fallback_depth"),
                rs.getBoolean("degraded"),
                rs.getBoolean("cache_hit"),
                rs.getString("request_masked"),
                rs.getString("response_masked"),
                new TokenUsage(
                        rs.getInt("input_tokens"),
                        rs.getInt("output_tokens"),
                        rs.getDouble("audio_seconds"),
                        rs.getInt("characters")),
                rs.getLong("latency_ms"),
                rs.getLong("shadow_cost_micro_usd"),
                rs.getString("guard_verdict"),
                AiCallOutcome.valueOf(rs.getString("outcome")),
                rs.getString("error_code"),
                rs.getString("trace_id"),
                rs.getObject("correlation_id", UUID.class),
                rs.getTimestamp("created_at").toInstant());
    }
}
