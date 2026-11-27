package com.toefly.tutoragent.application.port.out;

import com.toefly.tutoragent.domain.chat.ChatMessage;
import com.toefly.tutoragent.domain.chat.ChatThread;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Threads, messages and the stored events of streamed answers. */
public interface ChatRepository {

    void saveThread(ChatThread thread);

    Optional<ChatThread> thread(UUID threadId);

    /** Threads of a learner, latest message first; {@code after} is the last thread of the previous page. */
    List<ChatThread> threads(UUID userId, @Nullable UUID after, int limit);

    void deleteThread(UUID threadId);

    void saveMessage(ChatMessage message);

    Optional<ChatMessage> message(UUID messageId);

    /** Messages of a thread, oldest first, after the given message. */
    List<ChatMessage> messages(UUID threadId, @Nullable UUID after, int limit);

    /** The newest messages of a thread that ended normally, oldest first: the memory window. */
    List<ChatMessage> window(UUID threadId, int limit);

    /** Complete messages older than the window, for the summary. */
    List<ChatMessage> beforeWindow(UUID threadId, int window, @Nullable Instant after);

    int userMessagesSince(UUID userId, Instant since);

    void appendEvent(UUID messageId, int seq, String event, String data);

    List<StoredEvent> events(UUID messageId, int afterSeq);

    record StoredEvent(int seq, String event, String data) {}
}
