package com.toefly.tutoragent.adapter.out.persistence;

import com.toefly.tutoragent.application.port.out.ToolLog;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** tool_invocation rows (docs/06 §4.5). */
@Component
public class JdbcToolLogAdapter implements ToolLog {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcToolLogAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void record(
            UUID threadId, UUID messageId, String tool, Map<String, Object> args, String status, long latencyMs) {
        jdbc.sql("""
                        INSERT INTO tool_invocation (thread_id, message_id, tool, args_json, result_status, latency_ms, created_at)
                        VALUES (:t, :m, :tool, :a::jsonb, :s, :l, now())
                        """)
                .param("t", threadId)
                .param("m", messageId)
                .param("tool", tool)
                .param("a", json.writeValueAsString(args))
                .param("s", status)
                .param("l", (int) Math.min(Integer.MAX_VALUE, latencyMs))
                .update();
    }
}
