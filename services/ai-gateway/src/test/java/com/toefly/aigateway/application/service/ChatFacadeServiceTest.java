package com.toefly.aigateway.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.aigateway.adapter.out.provider.JtokkitTokenCounter;
import com.toefly.aigateway.adapter.out.schema.JsonOutputSchemaAdapter;
import com.toefly.aigateway.application.port.in.AiGuardBlockedException;
import com.toefly.aigateway.application.port.in.FacadeCommand;
import com.toefly.aigateway.application.port.in.FacadeResult;
import com.toefly.aigateway.application.port.out.ChatResult;
import com.toefly.aigateway.application.port.out.ConversationMaskingPort;
import com.toefly.aigateway.domain.call.TokenUsage;
import com.toefly.aigateway.domain.chat.ChatMessage;
import com.toefly.aigateway.domain.chat.ToolCall;
import com.toefly.aigateway.domain.chat.ToolSpec;
import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.routing.AiUseCase;
import com.toefly.aigateway.domain.safety.MaskingSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatFacadeServiceTest {

    final Gateway gateway = new Gateway();
    final Map<String, List<MaskingSession.Entry>> stored = new HashMap<>();
    final ConversationMaskingPort conversations = new ConversationMaskingPort() {
        @Override
        public MaskingSession load(String id) {
            return MaskingSession.restore(stored.getOrDefault(id, List.of()));
        }

        @Override
        public void save(String id, MaskingSession session) {
            stored.put(id, session.entries());
        }
    };
    final ChatFacadeService facade = new ChatFacadeService(
            () -> gateway.routes,
            new ProviderRegistry(List.of(gateway.groq, gateway.ollama)),
            gateway.safety,
            gateway.fallback,
            new JtokkitTokenCounter(),
            conversations,
            new JsonOutputSchemaAdapter(),
            gateway.journal,
            gateway.clock);

    FacadeCommand turn(List<ChatMessage> messages) {
        return new FacadeCommand(
                "tutor-agent-service",
                AiUseCase.TUTOR_CHAT,
                "tutor.chat.system",
                12,
                UUID.randomUUID(),
                "thr_1",
                List.of("Alina"),
                Priority.INTERACTIVE,
                "AI_TUTOR_CHAT",
                messages,
                List.of(new ToolSpec("addWordToVocabulary", "Adds a phrase", "{\"type\":\"object\"}")),
                "auto",
                null,
                null,
                null);
    }

    @Test
    void toolCallsComeBackUnmaskedAndThePersonKeepsItsPlaceholderNextTurn() {
        gateway.groq.fallback = r -> new ChatResult(
                null,
                List.of(new ToolCall("call_1", "addWordToVocabulary", "{\"phrase\":\"[PERSON_1] breaks the ice\"}")),
                "tool_calls",
                TokenUsage.tokens(50, 10),
                Map.of());

        FacadeResult first = facade.complete(turn(List.of(
                ChatMessage.system("You are Toefly Tutor."), ChatMessage.user("I'm Alina, save 'break the ice'"))));

        assertThat(first.message().toolCalls().getFirst().arguments())
                .isEqualTo("{\"phrase\":\"Alina breaks the ice\"}");
        assertThat(gateway.groq.requests.getFirst().tools())
                .extracting(ToolSpec::name)
                .containsExactly("addWordToVocabulary");

        gateway.groq.fallback = r -> Fakes.answer("Done, [PERSON_1]!");
        FacadeResult second = facade.complete(turn(List.of(
                ChatMessage.system("You are Toefly Tutor."),
                ChatMessage.user("I'm Alina, save 'break the ice'"),
                first.message(),
                new ChatMessage(ChatMessage.Role.TOOL, "{\"saved\":\"Alina breaks the ice\"}", List.of(), "call_1"))));

        String sent = gateway.groq.requests.getLast().messages().toString();
        assertThat(sent).doesNotContain("Alina").contains("[PERSON_1]");
        assertThat(second.message().content()).isEqualTo("Done, Alina!");
        assertThat(second.aiStamp().promptVersion()).isEqualTo(12);
    }

    @Test
    void streamedTurnIsUnmaskedAndAnUnsafeAnswerIsRefused() {
        gateway.groq.fallback = r -> Fakes.answer("Hi [PERSON_1], let's go.");
        StringBuilder shown = new StringBuilder();

        facade.stream(turn(List.of(ChatMessage.user("Hi, I'm Alina"))), shown::append);
        assertThat(shown).hasToString("Hi Alina, let's go.");

        gateway.guard.answer = "unsafe\nS1";
        assertThatThrownBy(() -> facade.complete(turn(List.of(ChatMessage.user("Tell me a story")))))
                .isInstanceOf(AiGuardBlockedException.class);
    }
}
