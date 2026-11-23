package com.toefly.tutoragent.adapter.in.web;

import com.toefly.tutoragent.adapter.in.web.dto.ChatAction;
import com.toefly.tutoragent.adapter.in.web.dto.ChatContextType;
import com.toefly.tutoragent.adapter.in.web.dto.ChatMessageRole;
import com.toefly.tutoragent.adapter.in.web.dto.ChatMessageStatus;
import com.toefly.tutoragent.adapter.in.web.dto.ExplanationLanguage;
import com.toefly.tutoragent.adapter.in.web.dto.TutorIntent;
import com.toefly.tutoragent.domain.chat.ChatContext;
import com.toefly.tutoragent.domain.chat.ChatMessage;
import com.toefly.tutoragent.domain.chat.ChatThread;
import com.toefly.tutoragent.domain.chat.PendingAction;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Chat domain to DTO of tutor-agent-service.yaml and back. */
final class ChatDtoMapper {

    private ChatDtoMapper() {}

    static com.toefly.tutoragent.adapter.in.web.dto.ChatThread thread(ChatThread t) {
        return new com.toefly.tutoragent.adapter.in.web.dto.ChatThread()
                .threadId(t.id())
                .title(t.title())
                .context(context(t.context()))
                .language(ExplanationLanguage.valueOf(t.language().name()))
                .createdAt(OffsetDateTime.ofInstant(t.createdAt(), ZoneOffset.UTC))
                .lastMessageAt(
                        t.lastMessageAt() == null ? null : OffsetDateTime.ofInstant(t.lastMessageAt(), ZoneOffset.UTC))
                .messageCount(t.messageCount());
    }

    static com.toefly.tutoragent.adapter.in.web.dto.ChatMessage message(ChatMessage m, List<PendingAction> actions) {
        return new com.toefly.tutoragent.adapter.in.web.dto.ChatMessage()
                .messageId(m.id())
                .role(ChatMessageRole.valueOf(m.role().name()))
                .text(m.text())
                .status(ChatMessageStatus.valueOf(m.status().name()))
                .intent(
                        m.intent() == null
                                ? null
                                : TutorIntent.valueOf(m.intent().name()))
                .context(context(m.context()))
                .actions(actions.stream().map(ChatDtoMapper::action).toList())
                .tools(m.tools())
                .createdAt(OffsetDateTime.ofInstant(m.createdAt(), ZoneOffset.UTC));
    }

    static ChatAction action(PendingAction a) {
        return new ChatAction()
                .actionId(a.id())
                .type(ChatAction.TypeEnum.ASSIGN_EXTRA_TASK)
                .title(a.title())
                .status(ChatAction.StatusEnum.valueOf(a.status().name()))
                .confirmUrl("/api/v1/pending-actions/" + a.id() + ":confirm");
    }

    static com.toefly.tutoragent.adapter.in.web.dto.@Nullable ChatContext context(@Nullable ChatContext c) {
        if (c == null) {
            return null;
        }
        return new com.toefly.tutoragent.adapter.in.web.dto.ChatContext()
                .type(ChatContextType.valueOf(c.type().name()))
                .ref(c.ref())
                .itemId(c.itemId())
                .label(c.label());
    }

    static @Nullable ChatContext context(com.toefly.tutoragent.adapter.in.web.dto.@Nullable ChatContext c) {
        if (c == null || c.getType() == null) {
            return null;
        }
        return new ChatContext(
                com.toefly.tutoragent.domain.chat.ChatContextType.valueOf(
                        c.getType().name()),
                c.getRef(),
                c.getItemId(),
                null);
    }
}
