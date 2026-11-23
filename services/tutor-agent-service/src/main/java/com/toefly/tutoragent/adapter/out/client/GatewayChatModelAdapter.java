package com.toefly.tutoragent.adapter.out.client;

import com.toefly.tutoragent.application.port.AiUnavailableException;
import com.toefly.tutoragent.application.port.out.ChatModelPort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Spring AI {@link OpenAiChatModel} pointed at the OpenAI-compatible facade of ai-gateway (docs/06 §4.6, docs/12 phase
 * 5c decision 4). Tools are definitions only: Spring AI 2.0 does not run them without ToolCallingAdvisor, so the
 * orchestrator drives the loop and can stream tool events. The service token is the API key of each call.
 */
public class GatewayChatModelAdapter implements ChatModelPort {

    private final String baseUrl;
    private final Supplier<String> token;
    private final Duration timeout;

    public GatewayChatModelAdapter(String gatewayUrl, Supplier<String> token, Duration timeout) {
        this.baseUrl = gatewayUrl + "/internal/openai/v1";
        this.token = token;
        this.timeout = timeout;
    }

    @Override
    public Turn stream(TurnRequest request, Consumer<String> onToken) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Toefly-Use-Case", request.useCase());
        headers.put("X-Toefly-Prompt-Key", request.promptKey());
        headers.put("X-Toefly-Prompt-Version", Integer.toString(request.promptVersion()));
        headers.put("X-Toefly-User-Id", request.userId().toString());
        headers.put("X-Toefly-Conversation-Id", request.conversationId());
        headers.put("X-Toefly-Priority", "INTERACTIVE");
        headers.put("X-Toefly-Feature", "tutor-chat");
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(token.get())
                .model("toefly-gateway")
                .maxRetries(0)
                .timeout(timeout)
                .streamUsage(true)
                .customHeaders(headers);
        if (!request.tools().isEmpty()) {
            options.toolCallbacks(request.tools().stream()
                    .map(GatewayChatModelAdapter::definition)
                    .toList());
        }
        OpenAiChatOptions built = options.build();
        OpenAiChatModel chat = OpenAiChatModel.builder().options(built).build();
        StringBuilder text = new StringBuilder();
        Map<String, ToolCall> calls = new LinkedHashMap<>();
        int in = 0;
        int out = 0;
        String aiCallId = null;
        try {
            for (ChatResponse chunk :
                    chat.stream(new Prompt(messages(request.messages()), built)).toIterable()) {
                Generation generation = chunk.getResult();
                if (generation != null) {
                    AssistantMessage output = generation.getOutput();
                    String delta = output.getText();
                    if (delta != null && !delta.isEmpty()) {
                        text.append(delta);
                        onToken.accept(delta);
                    }
                    for (AssistantMessage.ToolCall call : output.getToolCalls()) {
                        String id = call.id() == null || call.id().isBlank() ? "call_" + calls.size() : call.id();
                        calls.put(id, new ToolCall(id, call.name(), call.arguments()));
                    }
                }
                if (chunk.getMetadata() != null) {
                    if (chunk.getMetadata().getUsage() != null) {
                        Integer prompt = chunk.getMetadata().getUsage().getPromptTokens();
                        Integer completion = chunk.getMetadata().getUsage().getCompletionTokens();
                        in = prompt == null || prompt == 0 ? in : prompt;
                        out = completion == null || completion == 0 ? out : completion;
                    }
                    if (chunk.getMetadata().getId() != null
                            && !chunk.getMetadata().getId().isBlank()) {
                        aiCallId = chunk.getMetadata().getId();
                    }
                }
            }
        } catch (RuntimeException failure) {
            if (failure.getClass().getName().contains("Stopped")) {
                throw failure;
            }
            throw new AiUnavailableException("ai-gateway chat failed: " + failure.getMessage(), null, false, failure);
        }
        return new Turn(text.toString(), new ArrayList<>(calls.values()), aiCallId, in, out);
    }

    static List<org.springframework.ai.chat.messages.Message> messages(List<ChatModelPort.Message> messages) {
        Map<String, String> toolNames = new HashMap<>();
        List<org.springframework.ai.chat.messages.Message> out = new ArrayList<>();
        for (ChatModelPort.Message m : messages) {
            switch (m.role()) {
                case "system" -> out.add(new SystemMessage(m.content() == null ? "" : m.content()));
                case "user" -> out.add(new UserMessage(m.content() == null ? "" : m.content()));
                case "assistant" -> {
                    List<AssistantMessage.ToolCall> calls = new ArrayList<>();
                    for (ToolCall call : m.toolCalls()) {
                        toolNames.put(call.id(), call.name());
                        calls.add(new AssistantMessage.ToolCall(
                                call.id(), "function", call.name(), call.argumentsJson()));
                    }
                    out.add(AssistantMessage.builder()
                            .content(m.content() == null ? "" : m.content())
                            .toolCalls(calls)
                            .build());
                }
                case "tool" -> {
                    String id = m.toolCallId() == null ? "" : m.toolCallId();
                    out.add(ToolResponseMessage.builder()
                            .responses(List.of(new ToolResponseMessage.ToolResponse(
                                    id, toolNames.getOrDefault(id, "tool"), m.content() == null ? "" : m.content())))
                            .build());
                }
                default -> throw new IllegalArgumentException("Unknown role " + m.role());
            }
        }
        return out;
    }

    static ToolCallback definition(ToolSpec spec) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(spec.name())
                .description(spec.description())
                .inputSchema(spec.parametersJson())
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                throw new UnsupportedOperationException("Tools of Toefly Tutor run in ChatService: " + spec.name());
            }
        };
    }
}
