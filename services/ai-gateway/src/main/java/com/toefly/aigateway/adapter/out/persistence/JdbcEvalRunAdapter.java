package com.toefly.aigateway.adapter.out.persistence;

import com.toefly.aigateway.application.port.out.EvalRunRepository;
import com.toefly.aigateway.domain.prompt.EvalRun;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Eval run reports ({@code eval_run}). */
public class JdbcEvalRunAdapter implements EvalRunRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    public JdbcEvalRunAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(EvalRun run) {
        jdbc.sql("""
                        INSERT INTO eval_run (id, prompt_key, prompt_version, suite, mode, passed, metrics, report_url,
                            ran_at, received_at)
                        VALUES (:id, :key, :version, :suite, :mode, :passed, CAST(:metrics AS jsonb), :url, :ranAt,
                            :receivedAt)""")
                .param("id", run.id())
                .param("key", run.promptKey())
                .param("version", run.promptVersion())
                .param("suite", run.suite())
                .param("mode", run.mode())
                .param("passed", run.passed())
                .param("metrics", JSON.writeValueAsString(run.metrics()))
                .param("url", run.reportUrl())
                .param("ranAt", Timestamp.from(run.ranAt()))
                .param("receivedAt", Timestamp.from(run.receivedAt()))
                .update();
    }

    @Override
    public List<EvalRun> page(@Nullable String promptKey, int limit) {
        return jdbc.sql("""
                        SELECT * FROM eval_run WHERE (CAST(:key AS varchar) IS NULL OR prompt_key = :key)
                        ORDER BY received_at DESC LIMIT :limit""")
                .param("key", promptKey)
                .param("limit", Math.clamp(limit, 1, 100))
                .query(JdbcEvalRunAdapter::run)
                .list();
    }

    @Override
    public Optional<EvalRun> latestPassed(String promptKey, int version) {
        return jdbc.sql("""
                        SELECT * FROM eval_run WHERE prompt_key = :key AND prompt_version = :version AND passed
                        ORDER BY received_at DESC LIMIT 1""")
                .param("key", promptKey)
                .param("version", version)
                .query(JdbcEvalRunAdapter::run)
                .optional();
    }

    private static EvalRun run(ResultSet rs, int row) throws SQLException {
        Map<String, Double> metrics = JSON.readValue(rs.getString("metrics"), new TypeReference<>() {});
        return new EvalRun(
                rs.getObject("id", UUID.class),
                rs.getString("prompt_key"),
                rs.getInt("prompt_version"),
                rs.getString("suite"),
                rs.getString("mode"),
                rs.getBoolean("passed"),
                metrics,
                rs.getString("report_url"),
                rs.getTimestamp("ran_at").toInstant(),
                rs.getTimestamp("received_at").toInstant());
    }
}
