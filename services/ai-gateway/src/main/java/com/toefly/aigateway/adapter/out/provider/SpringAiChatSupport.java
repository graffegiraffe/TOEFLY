package com.toefly.aigateway.adapter.out.provider;

import com.toefly.aigateway.application.port.out.ChatProviderPort;
import com.toefly.aigateway.application.port.out.ChatRequest;
import com.toefly.aigateway.application.port.out.ChatResult;
import com.toefly.aigateway.domain.call.TokenUsage;
import com.toefly.aigateway.domain.chat.ChatMessage;
import com.toefly.aigateway.domain.chat.ToolCall;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/** Shared translation between the gateway's chat request and Spring AI {@link ChatModel}s. */
abstract class SpringAiChatSupport implements ChatProviderPort {

    protected abstract ChatModel model(ChatRequest request);

    protected abstract ChatOptions options(ChatRequest request, boolean stream);

    @Override
    public ChatResult call(ChatRequest request) {
        try {
            return result(model(request).call(prompt(request, false)));
        } catch (RuntimeException error) {
            throw ProviderErrors.map(provider().name(), error);
        }
    }

    /** Text deltas as they come; with tools the answer is fetched whole, so tool calls are never split. */
    @Override
    public ChatResult stream(ChatRequest request, Consumer<String> onDelta) {
        if (!request.tools().isEmpty()) {
            ChatResult whole = call(request);
            if (whole.content() != null && !whole.content().isEmpty()) {
                onDelta.accept(whole.content());
            }
            return whole;
        }
        StringBuilder text = new StringBuilder();
        ChatResponse last = null;
        try {
            for (ChatResponse chunk :
                    model(request).stream(prompt(request, true)).toIterable()) {
                last = chunk;
                Generation generation = chunk.getResult();
                if (generation != null && generation.getOutput().getText() != null) {
                    String delta = generation.getOutput().getText();
                    if (!delta.isEmpty()) {
                        text.append(delta);
                        onDelta.accept(delta);
                    }
                }
            }
        } catch (RuntimeException error) {
            throw ProviderErrors.map(provider().name(), error);
        }
        String finish = last == null || last.getResult() == null
                ? null
                : last.getResult().getMetadata().getFinishReason();
        return new ChatResult(text.toString(), List.of(), finish, usage(last), limits(last));
    }

    Prompt prompt(ChatRequest request, boolean stream) {
        Map<String, String> toolNames = new HashMap<>();
        List<Message> messages = new ArrayList<>();
        for (ChatMessage message : request.messages()) {
            messages.add(
                    switch (message.role()) {
                        case SYSTEM -> new SystemMessage(text(message.content()));
                        case USER -> new UserMessage(text(message.content()));
                        case ASSISTANT -> {
                            List<AssistantMessage.ToolCall> calls = new ArrayList<>();
                            for (ToolCall call : message.toolCalls()) {
                                toolNames.put(call.id(), call.name());
                                calls.add(new AssistantMessage.ToolCall(
                                        call.id(), "function", call.name(), call.arguments()));
                            }
                            yield AssistantMessage.builder()
                                    .content(message.content() == null ? "" : message.content())
                                    .toolCalls(calls)
                                    .build();
                        }
                        case TOOL -> {
                            String id = message.toolCallId() == null ? "" : message.toolCallId();
                            yield ToolResponseMessage.builder()
                                    .responses(List.of(new ToolResponseMessage.ToolResponse(
                                            id, toolNames.getOrDefault(id, "tool"), text(message.content()))))
                                    .build();
                        }
                    });
        }
        return new Prompt(messages, options(request, stream));
    }

    static ChatResult result(ChatResponse response) {
        Generation generation = response.getResult();
        String content = null;
        List<ToolCall> calls = new ArrayList<>();
        String finish = null;
        if (generation != null) {
            AssistantMessage output = generation.getOutput();
            content = output.getText();
            for (AssistantMessage.ToolCall call : output.getToolCalls()) {
                calls.add(new ToolCall(call.id(), call.name(), call.arguments()));
            }
            finish = generation.getMetadata().getFinishReason();
        }
        return new ChatResult(content, calls, finish, usage(response), limits(response));
    }

    static TokenUsage usage(@Nullable ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return TokenUsage.NONE;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return TokenUsage.NONE;
        }
        return TokenUsage.tokens(
                usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens());
    }

    /** Groq announces RPD as the request limit and TPM as the token limit (§10). */
    static Map<String, Long> limits(@Nullable ChatResponse response) {
        Map<String, Long> limits = new LinkedHashMap<>();
        if (response == null) {
            return limits;
        }
        ChatResponseMetadata metadata = response.getMetadata();
        RateLimit rate = metadata == null ? null : metadata.getRateLimit();
        if (rate != null) {
            if (rate.getRequestsLimit() != null && rate.getRequestsLimit() > 0) {
                limits.put("RPD", rate.getRequestsLimit());
            }
            if (rate.getTokensLimit() != null && rate.getTokensLimit() > 0) {
                limits.put("TPM", rate.getTokensLimit());
            }
        }
        return limits;
    }

    private static String text(@Nullable String content) {
        return content == null ? "" : content;
    }
}
