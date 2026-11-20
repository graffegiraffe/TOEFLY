package com.toefly.analytics.adapter.out.persistence;

import com.toefly.analytics.application.port.out.AiUsageRepository;
import com.toefly.analytics.domain.metrics.AiUsage;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAiUsageRepositoryAdapter implements AiUsageRepository {

    private final JdbcClient jdbc;

    JdbcAiUsageRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(AiUsage u) {
        jdbc.sql("""
                        INSERT INTO ai_usage_daily (day, provider, model, prompt_key, tokens_in, tokens_out,
                                                    cost_micro_usd, calls, fallback_calls, cache_hits)
                        VALUES (:day, :provider, :model, :prompt, :in, :out, :cost, :calls, :fallback, :cache)
                        ON CONFLICT (day, provider, model, prompt_key) DO UPDATE SET
                            tokens_in = ai_usage_daily.tokens_in + EXCLUDED.tokens_in,
                            tokens_out = ai_usage_daily.tokens_out + EXCLUDED.tokens_out,
                            cost_micro_usd = ai_usage_daily.cost_micro_usd + EXCLUDED.cost_micro_usd,
                            calls = ai_usage_daily.calls + EXCLUDED.calls,
                            fallback_calls = ai_usage_daily.fallback_calls + EXCLUDED.fallback_calls,
                            cache_hits = ai_usage_daily.cache_hits + EXCLUDED.cache_hits
                        """)
                .param("day", Date.valueOf(u.day()))
                .param("provider", u.provider())
                .param("model", u.model())
                .param("prompt", u.promptKey())
                .param("in", u.tokensIn())
                .param("out", u.tokensOut())
                .param("cost", u.costMicroUsd())
                .param("calls", u.calls())
                .param("fallback", u.fallbackCalls())
                .param("cache", u.cacheHits())
                .update();
    }

    @Override
    public List<AiUsage> rows(LocalDate from, LocalDate to) {
        return jdbc.sql("SELECT * FROM ai_usage_daily WHERE day >= :from AND day < :to ORDER BY day")
                .param("from", Date.valueOf(from))
                .param("to", Date.valueOf(to))
                .query((rs, n) -> new AiUsage(
                        rs.getDate("day").toLocalDate(),
                        rs.getString("provider"),
                        rs.getString("model"),
                        rs.getString("prompt_key"),
                        rs.getLong("tokens_in"),
                        rs.getLong("tokens_out"),
                        rs.getLong("cost_micro_usd"),
                        rs.getInt("calls"),
                        rs.getInt("fallback_calls"),
                        rs.getInt("cache_hits")))
                .list();
    }

    @Override
    public void reset() {
        jdbc.sql("DELETE FROM ai_usage_daily").update();
    }
}
