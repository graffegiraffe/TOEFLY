package com.toefly.tutoragent.adapter.in.web;

import com.toefly.common.web.CursorCodec;
import com.toefly.tutoragent.adapter.in.web.api.ChatThreadsApi;
import com.toefly.tutoragent.adapter.in.web.api.PendingActionsApi;
import com.toefly.tutoragent.adapter.in.web.dto.ChatMessagePage;
import com.toefly.tutoragent.adapter.in.web.dto.ChatThread;
import com.toefly.tutoragent.adapter.in.web.dto.ChatThreadPage;
import com.toefly.tutoragent.adapter.in.web.dto.ChatThreadPatch;
import com.toefly.tutoragent.adapter.in.web.dto.CreateChatThreadRequest;
import com.toefly.tutoragent.adapter.in.web.dto.PageInfo;
import com.toefly.tutoragent.adapter.in.web.dto.PendingActionResult;
import com.toefly.tutoragent.application.port.in.ChatUseCase;
import com.toefly.tutoragent.domain.chat.ChatMessage;
import com.toefly.tutoragent.domain.chat.PendingAction;
import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** Threads, history and confirmations of the chat (US-17.01, docs/05 §6.8). */
@RestController
@PreAuthorize("hasRole('STUDENT')")
class ChatThreadController implements ChatThreadsApi, PendingActionsApi {

    static final String THREADS = "chat-threads";
    static final String MESSAGES = "chat-messages";

    private final ChatUseCase chat;
    private final CurrentUser user;

    ChatThreadController(ChatUseCase chat, CurrentUser user) {
        this.chat = chat;
        this.user = user;
    }

    @Override
    public ResponseEntity<ChatThread> createChatThread(UUID idempotencyKey, CreateChatThreadRequest request) {
        var thread = chat.createThread(user.id(), request.getTitle(), ChatDtoMapper.context(request.getContext()));
        return ResponseEntity.created(URI.create("/api/v1/chat-threads/" + thread.id()))
                .body(ChatDtoMapper.thread(thread));
    }

    @Override
    public ResponseEntity<ChatThreadPage> listChatThreads(Integer limit, @Nullable String cursor) {
        int size = CursorCodec.limit(limit);
        UUID after = cursor == null
                ? null
                : UUID.fromString(CursorCodec.decode(cursor, THREADS).getFirst());
        var page = chat.threads(user.id(), after, size + 1);
        boolean more = page.size() > size;
        var items = more ? page.subList(0, size) : page;
        return ResponseEntity.ok(new ChatThreadPage()
                .items(items.stream().map(ChatDtoMapper::thread).toList())
                .page(new PageInfo()
                        .hasMore(more)
                        .limit(size)
                        .nextCursor(
                                more
                                        ? CursorCodec.encode(
                                                THREADS,
                                                List.of(items.getLast().id().toString()))
                                        : null)));
    }

    @Override
    public ResponseEntity<ChatThread> getChatThread(UUID threadId) {
        return ResponseEntity.ok(ChatDtoMapper.thread(chat.thread(user.id(), threadId)));
    }

    @Override
    public ResponseEntity<ChatThread> patchChatThread(UUID threadId, ChatThreadPatch patch) {
        return ResponseEntity.ok(ChatDtoMapper.thread(chat.updateThread(
                user.id(),
                threadId,
                patch.getTitle(),
                patch.getLanguage() == null
                        ? null
                        : ExplanationLanguage.valueOf(patch.getLanguage().name()))));
    }

    @Override
    public ResponseEntity<Void> deleteChatThread(UUID threadId) {
        chat.deleteThread(user.id(), threadId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ChatMessagePage> listChatMessages(UUID threadId, Integer limit, @Nullable String cursor) {
        int size = CursorCodec.limit(limit);
        UUID after = cursor == null
                ? null
                : UUID.fromString(CursorCodec.decode(cursor, MESSAGES).getFirst());
        List<ChatMessage> page = chat.messages(user.id(), threadId, after, size + 1);
        boolean more = page.size() > size;
        List<ChatMessage> items = more ? page.subList(0, size) : page;
        return ResponseEntity.ok(new ChatMessagePage()
                .items(items.stream()
                        .map(m -> ChatDtoMapper.message(m, chat.actionsOf(m.id())))
                        .toList())
                .page(new PageInfo()
                        .hasMore(more)
                        .limit(size)
                        .nextCursor(
                                more
                                        ? CursorCodec.encode(
                                                MESSAGES,
                                                List.of(items.getLast().id().toString()))
                                        : null)));
    }

    @Override
    public ResponseEntity<PendingActionResult> confirmPendingAction(UUID actionId) {
        PendingAction action = chat.confirm(user.id(), actionId);
        return ResponseEntity.ok(new PendingActionResult()
                .actionId(action.id())
                .type(PendingActionResult.TypeEnum.ASSIGN_EXTRA_TASK)
                .status(PendingActionResult.StatusEnum.valueOf(action.status().name()))
                .weekTaskId(action.resultId()));
    }
}
