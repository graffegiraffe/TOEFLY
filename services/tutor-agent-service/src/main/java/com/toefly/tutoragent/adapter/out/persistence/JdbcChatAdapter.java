package com.toefly.tutoragent.adapter.out.persistence;

import com.toefly.tutoragent.application.port.out.ChatRepository;
import com.toefly.tutoragent.domain.chat.ChatContext;
import com.toefly.tutoragent.domain.chat.ChatContextType;
import com.toefly.tutoragent.domain.chat.ChatMessage;
import com.toefly.tutoragent.domain.chat.ChatThread;
import com.toefly.tutoragent.domain.chat.TutorIntent;
import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Threads, messages and stored stream events (docs/06 §4.6, §4.9). */
@Component
public class JdbcChatAdapter implements ChatRepository {

    private final JdbcClient jdbc;

    public JdbcChatAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveThread(ChatThread t) {
        ChatContext c = t.context();
        jdbc.sql("""
                        INSERT INTO chat_thread (id, user_id, title, context_type, context_ref, context_item_id, context_label,
                            language, summary, summarized_upto, message_count, created_at, last_message_at)
                        VALUES (:id, :u, :title, :ct, :cr, :ci, :cl, :lang, :sum, :upto, :count, :created, :last)
                        ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, language = EXCLUDED.language,
                            summary = EXCLUDED.summary, summarized_upto = EXCLUDED.summarized_upto,
                            message_count = EXCLUDED.message_count, last_message_at = EXCLUDED.last_message_at
                        """)
                .param("id", t.id())
                .param("u", t.userId())
                .param("title", t.title())
                .param("ct", c == null ? null : c.type().name())
                .param("cr", c == null ? null : c.ref())
                .param("ci", c == null ? null : c.itemId())
                .param("cl", c == null ? null : c.label())
                .param("lang", t.language().name())
                .param("sum", t.summary())
                .param("upto", ts(t.summarizedUpto()))
                .param("count", t.messageCount())
                .param("created", ts(t.createdAt()))
                .param("last", ts(t.lastMessageAt()))
                .update();
    }

    @Override
    public Optional<ChatThread> thread(UUID threadId) {
        return jdbc.sql("SELECT * FROM chat_thread WHERE id = :id")
                .param("id", threadId)
                .query((rs, n) -> thread(rs))
                .optional();
    }

    @Override
    public List<ChatThread> threads(UUID userId, @Nullable UUID after, int limit) {
        if (after == null) {
            return jdbc.sql("""
                            SELECT * FROM chat_thread WHERE user_id = :u
                            ORDER BY COALESCE(last_message_at, created_at) DESC, id LIMIT :l
                            """)
                    .param("u", userId)
                    .param("l", limit)
                    .query((rs, n) -> thread(rs))
                    .list();
        }
        return jdbc.sql("""
                        SELECT t.* FROM chat_thread t, chat_thread a
                        WHERE a.id = :after AND t.user_id = :u
                          AND (COALESCE(t.last_message_at, t.created_at), t.id) < (COALESCE(a.last_message_at, a.created_at), a.id)
                        ORDER BY COALESCE(t.last_message_at, t.created_at) DESC, t.id LIMIT :l
                        """)
                .param("after", after)
                .param("u", userId)
                .param("l", limit)
                .query((rs, n) -> thread(rs))
                .list();
    }

    @Override
    public void deleteThread(UUID threadId) {
        jdbc.sql("DELETE FROM chat_thread WHERE id = :id").param("id", threadId).update();
    }

    @Override
    public void saveMessage(ChatMessage m) {
        ChatContext c = m.context();
        jdbc.sql("""
                        INSERT INTO chat_message (id, thread_id, user_id, role, text, status, intent, context_type, context_ref,
                            context_item_id, context_label, tools, ai_stamp, tokens_in, tokens_out, created_at)
                        VALUES (:id, :t, :u, :role, :text, :status, :intent, :ct, :cr, :ci, :cl, :tools, :stamp::jsonb, :in, :out, :at)
                        ON CONFLICT (id) DO UPDATE SET text = EXCLUDED.text, status = EXCLUDED.status, intent = EXCLUDED.intent,
                            tools = EXCLUDED.tools, ai_stamp = EXCLUDED.ai_stamp, tokens_in = EXCLUDED.tokens_in,
                            tokens_out = EXCLUDED.tokens_out
                        """)
                .param("id", m.id())
                .param("t", m.threadId())
                .param("u", m.userId())
                .param("role", m.role().name())
                .param("text", m.text())
                .param("status", m.status().name())
                .param("intent", m.intent() == null ? null : m.intent().name())
                .param("ct", c == null ? null : c.type().name())
                .param("cr", c == null ? null : c.ref())
                .param("ci", c == null ? null : c.itemId())
                .param("cl", c == null ? null : c.label())
                .param("tools", m.tools().toArray(String[]::new))
                .param(
                        "stamp",
                        m.aiCallId() == null
                                ? null
                                : "{\"aiCallId\":\"" + m.aiCallId().replace("\"", "") + "\"}")
                .param("in", m.tokensIn())
                .param("out", m.tokensOut())
                .param("at", ts(m.createdAt()))
                .update();
    }

    @Override
    public Optional<ChatMessage> message(UUID messageId) {
        return jdbc.sql("SELECT * FROM chat_message WHERE id = :id")
                .param("id", messageId)
                .query((rs, n) -> message(rs))
                .optional();
    }

    @Override
    public List<ChatMessage> messages(UUID threadId, @Nullable UUID after, int limit) {
        return jdbc.sql("""
                        SELECT * FROM chat_message m WHERE thread_id = :t
                          AND (CAST(:after AS uuid) IS NULL OR (created_at, id) > (
                              SELECT created_at, id FROM chat_message WHERE id = CAST(:after AS uuid)))
                        ORDER BY created_at, id LIMIT :l
                        """)
                .param("t", threadId)
                .param("after", after)
                .param("l", limit)
                .query((rs, n) -> message(rs))
                .list();
    }

    @Override
    public List<ChatMessage> window(UUID threadId, int limit) {
        List<ChatMessage> newest = new ArrayList<>(jdbc.sql("""
                        SELECT * FROM chat_message WHERE thread_id = :t ORDER BY created_at DESC, id DESC LIMIT :l
                        """)
                .param("t", threadId)
                .param("l", limit)
                .query((rs, n) -> message(rs))
                .list());
        Collections.reverse(newest);
        return newest;
    }

    @Override
    public List<ChatMessage> beforeWindow(UUID threadId, int window, @Nullable Instant after) {
        return jdbc.sql("""
                        SELECT * FROM chat_message WHERE thread_id = :t AND status = 'COMPLETE'
                          AND created_at > :after
                          AND id NOT IN (SELECT id FROM chat_message WHERE thread_id = :t
                                         ORDER BY created_at DESC, id DESC LIMIT :w)
                        ORDER BY created_at, id
                        """)
                .param("t", threadId)
                .param("after", ts(after == null ? Instant.EPOCH : after))
                .param("w", window)
                .query((rs, n) -> message(rs))
                .list();
    }

    @Override
    public int userMessagesSince(UUID userId, Instant since) {
        return jdbc.sql("SELECT count(*) FROM chat_message WHERE user_id = :u AND role = 'USER' AND created_at >= :s")
                .param("u", userId)
                .param("s", ts(since))
                .query(Integer.class)
                .single();
    }

    @Override
    public void appendEvent(UUID messageId, int seq, String event, String data) {
        jdbc.sql("""
                        INSERT INTO chat_stream_event (message_id, seq, event, data, created_at) VALUES (:m, :s, :e, :d, now())
                        ON CONFLICT DO NOTHING
                        """)
                .param("m", messageId)
                .param("s", seq)
                .param("e", event)
                .param("d", data)
                .update();
    }

    @Override
    public List<StoredEvent> events(UUID messageId, int afterSeq) {
        return jdbc.sql(
                        "SELECT seq, event, data FROM chat_stream_event WHERE message_id = :m AND seq > :s ORDER BY seq")
                .param("m", messageId)
                .param("s", afterSeq)
                .query((rs, n) -> new StoredEvent(rs.getInt("seq"), rs.getString("event"), rs.getString("data")))
                .list();
    }

    private static ChatThread thread(ResultSet rs) throws SQLException {
        return new ChatThread(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("title"),
                context(rs),
                ExplanationLanguage.of(rs.getString("language")),
                rs.getString("summary"),
                instant(rs.getTimestamp("summarized_upto")),
                rs.getInt("message_count"),
                rs.getTimestamp("created_at").toInstant(),
                instant(rs.getTimestamp("last_message_at")));
    }

    private static ChatMessage message(ResultSet rs) throws SQLException {
        Array tools = rs.getArray("tools");
        String stamp = rs.getString("ai_stamp");
        String intent = rs.getString("intent");
        return new ChatMessage(
                rs.getObject("id", UUID.class),
                rs.getObject("thread_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                ChatMessage.Role.valueOf(rs.getString("role")),
                rs.getString("text"),
                ChatMessage.Status.valueOf(rs.getString("status")),
                intent == null ? null : TutorIntent.valueOf(intent),
                context(rs),
                tools == null ? List.of() : Arrays.asList((String[]) tools.getArray()),
                stamp == null ? null : stamp.replaceAll(".*\"aiCallId\"\\s*:\\s*\"([^\"]*)\".*", "$1"),
                (Integer) rs.getObject("tokens_in"),
                (Integer) rs.getObject("tokens_out"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static @Nullable ChatContext context(ResultSet rs) throws SQLException {
        String type = rs.getString("context_type");
        if (type == null) {
            return null;
        }
        return new ChatContext(
                ChatContextType.valueOf(type),
                rs.getObject("context_ref", UUID.class),
                rs.getObject("context_item_id", UUID.class),
                rs.getString("context_label"));
    }

    private static @Nullable Timestamp ts(@Nullable Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static @Nullable Instant instant(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
