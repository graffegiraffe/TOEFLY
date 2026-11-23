package com.toefly.tutoragent.adapter.out.persistence;

import com.toefly.tutoragent.application.port.out.PendingActionRepository;
import com.toefly.tutoragent.domain.chat.PendingAction;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Proposed changes of the agent. */
@Component
public class JdbcPendingActionAdapter implements PendingActionRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcPendingActionAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void save(PendingAction a) {
        jdbc.sql("""
                        INSERT INTO pending_action (id, user_id, thread_id, message_id, type, title, payload, status, result,
                            created_at, expires_at)
                        VALUES (:id, :u, :t, :m, :type, :title, :p::jsonb, :s, :r::jsonb, :c, :e)
                        ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, result = EXCLUDED.result
                        """)
                .param("id", a.id())
                .param("u", a.userId())
                .param("t", a.threadId())
                .param("m", a.messageId())
                .param("type", a.type())
                .param("title", a.title())
                .param("p", json.writeValueAsString(a.payload()))
                .param("s", a.status().name())
                .param("r", a.resultId() == null ? null : "{\"resultId\":\"" + a.resultId() + "\"}")
                .param("c", Timestamp.from(a.createdAt()))
                .param("e", Timestamp.from(a.expiresAt()))
                .update();
    }

    @Override
    public Optional<PendingAction> find(UUID actionId) {
        return jdbc.sql("SELECT * FROM pending_action WHERE id = :id")
                .param("id", actionId)
                .query((rs, n) -> action(rs))
                .optional();
    }

    @Override
    public List<PendingAction> ofMessage(UUID messageId) {
        return jdbc.sql("SELECT * FROM pending_action WHERE message_id = :m ORDER BY created_at")
                .param("m", messageId)
                .query((rs, n) -> action(rs))
                .list();
    }

    private PendingAction action(ResultSet rs) throws SQLException {
        String result = rs.getString("result");
        return new PendingAction(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("thread_id", UUID.class),
                rs.getObject("message_id", UUID.class),
                rs.getString("type"),
                rs.getString("title"),
                json.readValue(rs.getString("payload"), new TypeReference<Map<String, Object>>() {}),
                PendingAction.Status.valueOf(rs.getString("status")),
                result == null
                        ? null
                        : UUID.fromString(json.readTree(result).path("resultId").asString()),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant());
    }
}
