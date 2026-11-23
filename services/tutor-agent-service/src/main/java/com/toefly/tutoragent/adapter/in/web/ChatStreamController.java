package com.toefly.tutoragent.adapter.in.web;

import com.toefly.tutoragent.application.port.in.ChatUseCase;
import com.toefly.tutoragent.domain.chat.ChatContext;
import com.toefly.tutoragent.domain.chat.ChatContextType;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The answer stream of docs/06 §4.9, written by hand: refusals before AI are Problem Details, then {@code meta},
 * {@code tool}, {@code token}, {@code action}, {@code retract}, {@code done} or {@code error} with numbered ids, and
 * {@code : ping} every 15 s. Events are stored, so a client resumes with Last-Event-ID (AC-17.01.4).
 */
@RestController
@PreAuthorize("hasRole('STUDENT')")
class ChatStreamController {

    static final long TIMEOUT_MS = Duration.ofMinutes(3).toMillis();
    static final int MAX_TEXT = 2000;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ChatUseCase chat;
    private final CurrentUser user;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService pings =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
    private final Map<UUID, List<SseEmitter>> listeners = new ConcurrentHashMap<>();

    ChatStreamController(ChatUseCase chat, CurrentUser user) {
        this.chat = chat;
        this.user = user;
    }

    @PostMapping(
            value = "/api/v1/chat-threads/{threadId}/messages",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
    ResponseEntity<SseEmitter> send(@PathVariable UUID threadId, @RequestBody JsonNode body) {
        String text = body.path("text").asString("").strip();
        if (text.isEmpty() || text.length() > MAX_TEXT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation-failed", "text must have 1 to 2000 characters.");
        }
        ChatUseCase.ContextChoice choice = !body.has("context")
                ? ChatUseCase.ContextChoice.thread()
                : new ChatUseCase.ContextChoice(false, context(body.path("context")));
        ChatUseCase.Turn turn = chat.accept(user.id(), user.token(), threadId, text, choice);
        SseEmitter emitter = open(turn.answerId());
        workers.submit(() -> {
            try {
                chat.answer(
                        turn,
                        (seq, event, data) -> broadcast(turn.answerId(), seq, event, JSON.writeValueAsString(data)));
            } finally {
                close(turn.answerId());
            }
        });
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    @GetMapping(
            value = "/api/v1/chat-threads/{threadId}/messages/{messageId}/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<SseEmitter> resume(
            @PathVariable UUID threadId,
            @PathVariable UUID messageId,
            @RequestHeader(value = "Last-Event-ID", required = false) @Nullable String lastEventId) {
        int after = lastEventId == null || !lastEventId.matches("\\d{1,9}") ? 0 : Integer.parseInt(lastEventId);
        List<ChatUseCase.StoredEvent> past = chat.eventsAfter(user.id(), threadId, messageId, after);
        boolean live = chat.streaming(messageId);
        SseEmitter emitter = live ? open(messageId) : new SseEmitter(TIMEOUT_MS);
        for (ChatUseCase.StoredEvent e : past) {
            send(emitter, e.seq(), e.event(), e.data());
        }
        if (!live) {
            emitter.complete();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    @DeleteMapping("/api/v1/chat-threads/{threadId}/messages/{messageId}/stream")
    ResponseEntity<Void> stop(@PathVariable UUID threadId, @PathVariable UUID messageId) {
        chat.stop(user.id(), threadId, messageId);
        return ResponseEntity.noContent().build();
    }

    private SseEmitter open(UUID messageId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = listeners.computeIfAbsent(messageId, id -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        var ping = pings.scheduleAtFixedRate(
                () -> {
                    try {
                        emitter.send(SseEmitter.event().comment("ping"));
                    } catch (IOException | IllegalStateException gone) {
                        list.remove(emitter);
                    }
                },
                15,
                15,
                TimeUnit.SECONDS);
        Runnable cleanup = () -> {
            ping.cancel(false);
            list.remove(emitter);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        return emitter;
    }

    private void broadcast(UUID messageId, int seq, String event, String data) {
        for (SseEmitter emitter : listeners.getOrDefault(messageId, List.of())) {
            if (!send(emitter, seq, event, data)) {
                listeners.getOrDefault(messageId, new CopyOnWriteArrayList<>()).remove(emitter);
            }
        }
    }

    private void close(UUID messageId) {
        List<SseEmitter> list = listeners.remove(messageId);
        if (list != null) {
            list.forEach(SseEmitter::complete);
        }
    }

    private static boolean send(SseEmitter emitter, int seq, String event, String data) {
        try {
            emitter.send(
                    SseEmitter.event().id(Integer.toString(seq)).name(event).data(data, MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException | IllegalStateException gone) {
            return false;
        }
    }

    private static @Nullable ChatContext context(JsonNode node) {
        if (node.isNull() || node.isMissingNode() || !node.path("type").isString()) {
            return null;
        }
        try {
            return new ChatContext(
                    ChatContextType.valueOf(node.path("type").asString()),
                    uuid(node.path("ref")),
                    uuid(node.path("itemId")),
                    null);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation-failed", "context is invalid.");
        }
    }

    private static @Nullable UUID uuid(JsonNode node) {
        return node.isString() ? UUID.fromString(node.asString()) : null;
    }
}
