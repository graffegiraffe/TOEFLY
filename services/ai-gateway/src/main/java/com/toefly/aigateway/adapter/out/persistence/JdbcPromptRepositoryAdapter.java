package com.toefly.aigateway.adapter.out.persistence;

import com.toefly.aigateway.application.port.out.PromptRepository;
import com.toefly.aigateway.domain.prompt.PromptParams;
import com.toefly.aigateway.domain.prompt.PromptStatus;
import com.toefly.aigateway.domain.prompt.PromptTemplate;
import com.toefly.aigateway.domain.prompt.PromptVersion;
import com.toefly.aigateway.domain.routing.AiUseCase;
import com.toefly.aigateway.domain.safety.GuardPolicy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Prompt templates and their immutable versions in PostgreSQL. */
public class JdbcPromptRepositoryAdapter implements PromptRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    public JdbcPromptRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PromptTemplate> template(String key) {
        return jdbc.sql("SELECT * FROM prompt_template WHERE prompt_key = :key")
                .param("key", key)
                .query(JdbcPromptRepositoryAdapter::template)
                .optional();
    }

    @Override
    public List<PromptTemplate> templates() {
        return jdbc.sql("SELECT * FROM prompt_template ORDER BY prompt_key")
                .query(JdbcPromptRepositoryAdapter::template)
                .list();
    }

    @Override
    public Optional<PromptVersion> version(String key, int version) {
        return jdbc.sql("SELECT * FROM prompt_version WHERE prompt_key = :key AND version = :version")
                .param("key", key)
                .param("version", version)
                .query(JdbcPromptRepositoryAdapter::version)
                .optional();
    }

    @Override
    public List<PromptVersion> versions(String key) {
        return jdbc.sql("SELECT * FROM prompt_version WHERE prompt_key = :key ORDER BY version")
                .param("key", key)
                .query(JdbcPromptRepositoryAdapter::version)
                .list();
    }

    @Override
    public void save(PromptTemplate template) {
        jdbc.sql("""
                        INSERT INTO prompt_template (prompt_key, use_case, description, owner_service, active_version,
                            canary_version, canary_percent, updated_at)
                        VALUES (:key, :useCase, :description, :owner, :active, :canary, :percent, now())
                        ON CONFLICT (prompt_key) DO UPDATE SET
                            active_version = EXCLUDED.active_version,
                            canary_version = EXCLUDED.canary_version,
                            canary_percent = EXCLUDED.canary_percent,
                            description = EXCLUDED.description,
                            updated_at = now()""")
                .param("key", template.key())
                .param("useCase", template.useCase().name())
                .param("description", template.description())
                .param("owner", template.ownerService())
                .param("active", template.activeVersion())
                .param("canary", template.canaryVersion())
                .param("percent", template.canaryPercent())
                .update();
    }

    @Override
    public void add(PromptVersion version) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (version.params().temperature() != null) {
            params.put("temperature", version.params().temperature());
        }
        if (version.params().maxOutputTokens() != null) {
            params.put("maxOutputTokens", version.params().maxOutputTokens());
        }
        if (version.params().reasoningEffort() != null) {
            params.put("reasoningEffort", version.params().reasoningEffort());
        }
        jdbc.sql("""
                        INSERT INTO prompt_version (prompt_key, version, use_case, description, system_text,
                            user_template, output_schema, params, max_input_tokens, untrusted, guard_policy,
                            eval_suite, status, source_hash, created_by, created_at)
                        VALUES (:key, :version, :useCase, :description, :system, :user, :schema,
                            CAST(:params AS jsonb), :maxInput, CAST(:untrusted AS jsonb), :guard, :evalSuite,
                            :status, :hash, :createdBy, :createdAt)""")
                .param("key", version.key())
                .param("version", version.version())
                .param("useCase", version.useCase().name())
                .param("description", version.description())
                .param("system", version.system())
                .param("user", version.user())
                .param("schema", version.outputSchema())
                .param("params", JSON.writeValueAsString(params))
                .param("maxInput", version.maxInputTokens())
                .param(
                        "untrusted",
                        JSON.writeValueAsString(
                                version.untrusted().stream().sorted().toList()))
                .param("guard", version.guardPolicy().name())
                .param("evalSuite", version.evalSuite())
                .param("status", version.status().name())
                .param("hash", version.sourceHash())
                .param("createdBy", version.createdBy())
                .param("createdAt", Timestamp.from(version.createdAt()))
                .update();
    }

    @Override
    public void updateStatus(String key, int version, PromptStatus status) {
        jdbc.sql("UPDATE prompt_version SET status = :status WHERE prompt_key = :key AND version = :version")
                .param("status", status.name())
                .param("key", key)
                .param("version", version)
                .update();
    }

    private static PromptTemplate template(ResultSet rs, int row) throws SQLException {
        return new PromptTemplate(
                rs.getString("prompt_key"),
                AiUseCase.valueOf(rs.getString("use_case")),
                rs.getString("description"),
                rs.getString("owner_service"),
                (Integer) rs.getObject("active_version"),
                (Integer) rs.getObject("canary_version"),
                rs.getInt("canary_percent"));
    }

    private static PromptVersion version(ResultSet rs, int row) throws SQLException {
        Map<String, Object> params = JSON.readValue(rs.getString("params"), new TypeReference<>() {});
        Set<String> untrusted =
                new HashSet<>(JSON.readValue(rs.getString("untrusted"), new TypeReference<List<String>>() {}));
        return new PromptVersion(
                rs.getString("prompt_key"),
                rs.getInt("version"),
                AiUseCase.valueOf(rs.getString("use_case")),
                rs.getString("description"),
                rs.getString("system_text"),
                rs.getString("user_template"),
                rs.getString("output_schema"),
                new PromptParams(
                        params.get("temperature") instanceof Number n ? n.doubleValue() : null,
                        params.get("maxOutputTokens") instanceof Number n ? n.intValue() : null,
                        params.get("reasoningEffort") == null
                                ? null
                                : params.get("reasoningEffort").toString()),
                rs.getInt("max_input_tokens"),
                untrusted,
                GuardPolicy.valueOf(rs.getString("guard_policy")),
                rs.getString("eval_suite"),
                PromptStatus.valueOf(rs.getString("status")),
                rs.getString("source_hash"),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant());
    }
}
