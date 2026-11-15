package com.toefly.aigateway.adapter.out.provider;

import com.toefly.aigateway.application.port.out.ChatRequest;
import com.toefly.aigateway.domain.routing.AiProvider;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Groq through its OpenAI-compatible endpoint with Spring AI {@link OpenAiChatModel} (§2.1). Retries of the SDK are
 * off: the fallback chain owns repeats, quota and breakers.
 */
public class GroqChatAdapter extends SpringAiChatSupport {

    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;
    private final Map<String, OpenAiChatModel> models = new ConcurrentHashMap<>();

    public GroqChatAdapter(String baseUrl, String apiKey, Duration timeout) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeout = timeout;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.GROQ;
    }

    @Override
    protected ChatModel model(ChatRequest request) {
        if (apiKey.isBlank()) {
            throw new com.toefly.aigateway.application.port.out.ProviderFailure(
                    com.toefly.aigateway.application.port.out.ProviderFailure.Kind.REJECTED,
                    "GROQ_API_KEY is not set",
                    null,
                    null);
        }
        return models.computeIfAbsent(
                request.model().model(),
                name -> OpenAiChatModel.builder()
                        .options(OpenAiChatOptions.builder()
                                .baseUrl(baseUrl)
                                .apiKey(apiKey)
                                .model(name)
                                .maxRetries(0)
                                .timeout(timeout)
                                .build())
                        .build());
    }

    @Override
    protected ChatOptions options(ChatRequest request, boolean stream) {
        String name = request.model().model();
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .maxRetries(0)
                .timeout(timeout)
                .model(name)
                .temperature(request.params().temperature());
        if (request.params().maxOutputTokens() > 0) {
            options.maxCompletionTokens(request.params().maxOutputTokens());
        }
        if (name.startsWith("openai/gpt-oss") && request.params().reasoningEffort() != null) {
            options.reasoningEffort(request.params().reasoningEffort());
        }
        if (request.jsonSchema() != null) {
            OpenAiChatModel.ResponseFormat format = new OpenAiChatModel.ResponseFormat();
            format.setType(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT);
            options.responseFormat(format);
        }
        if (!request.tools().isEmpty()) {
            options.toolCallbacks(request.tools().stream()
                    .map(DefinitionOnlyTool::of)
                    .map(tool -> (org.springframework.ai.tool.ToolCallback) tool)
                    .toList());
            if (request.toolChoice() != null) {
                options.toolChoice(request.toolChoice());
            }
        }
        if (stream) {
            options.streamUsage(true);
        }
        return options.build();
    }
}
