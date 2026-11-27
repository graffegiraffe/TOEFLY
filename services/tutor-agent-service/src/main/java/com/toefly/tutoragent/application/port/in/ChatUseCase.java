package com.toefly.tutoragent.application.port.in;

import com.toefly.tutoragent.domain.chat.ChatContext;
import com.toefly.tutoragent.domain.chat.ChatMessage;
import com.toefly.tutoragent.domain.chat.ChatThread;
import com.toefly.tutoragent.domain.chat.PendingAction;
import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The chat of Toefly Tutor (US-17.01–17.06). */
public interface ChatUseCase {

    ChatThread createThread(UUID userId, @Nullable String title, @Nullable ChatContext context);

    List<ChatThread> threads(UUID userId, @Nullable UUID after, int limit);

    ChatThread thread(UUID userId, UUID threadId);

    ChatThread updateThread(UUID userId, UUID threadId, @Nullable String title, @Nullable ExplanationLanguage language);

    void deleteThread(UUID userId, UUID threadId);

    List<ChatMessage> messages(UUID userId, UUID threadId, @Nullable UUID after, int limit);

    List<PendingAction> actionsOf(UUID messageId);

    /**
     * Checks the request and stores the student message; refusals before AI are thrown as
     * {@link ChatRejectedException} or {@link EntitlementRequiredException}. The answer is produced by {@link #answer}.
     */
    Turn accept(UUID userId, @Nullable String bearer, UUID threadId, String text, ContextChoice context);

    /** Produces the answer of an accepted turn, sending its events to the sink and storing them for resuming. */
    void answer(Turn turn, ChatStreamSink sink);

    /** Stops a streaming answer; the partial text stays as INTERRUPTED. */
    void stop(UUID userId, UUID threadId, UUID messageId);

    /** Stored events of an answer after the given sequence number; empty for a foreign message. */
    List<StoredEvent> eventsAfter(UUID userId, UUID threadId, UUID messageId, int afterSeq);

    boolean streaming(UUID messageId);

    PendingAction confirm(UUID userId, UUID actionId);

    /** The context of one message: the thread's, none (the chip was removed) or a new one. */
    record ContextChoice(boolean keepThread, @Nullable ChatContext context) {
        public static ContextChoice thread() {
            return new ContextChoice(true, null);
        }
    }

    record Turn(
            UUID userId,
            @Nullable String bearer,
            UUID threadId,
            UUID userMessageId,
            UUID answerId,
            String text,
            @Nullable ChatContext context) {}

    record StoredEvent(int seq, String event, String data) {}
}
