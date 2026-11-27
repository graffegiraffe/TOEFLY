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
 * A provider with an OpenAI-compatible endpoint (Groq, Z.ai, OpenRouter) through Spring AI {@link OpenAiChatModel}
 * (§2.1, ADR-0032, ADR-0033).
 * Retries of the SDK are off: the fallback chain owns repeats, quota and breakers.
 */
public class OpenAiCompatibleChatAdapter extends SpringAiChatSupport {

    static final Map<String, Object> OPENROUTER_ROUTING =
            Map.of("provider", Map.of("require_parameters", true, "data_collection", "deny"));

    private final AiProvider provider;
    private final String keyName;
    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;
    private final Map<String, OpenAiChatModel> models = new ConcurrentHashMap<>();

    public OpenAiCompatibleChatAdapter(
            AiProvider provider, String keyName, String baseUrl, String apiKey, Duration timeout) {
        this.provider = provider;
        this.keyName = keyName;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeout = timeout;
    }

    @Override
    public AiProvider provider() {
        return provider;
    }

    @Override
    protected ChatModel model(ChatRequest request) {
        if (apiKey.isBlank()) {
            throw new com.toefly.aigateway.application.port.out.ProviderFailure(
                    com.toefly.aigateway.application.port.out.ProviderFailure.Kind.REJECTED,
                    keyName + " is not set",
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
        if (provider == AiProvider.ZAI) {
            // GLM thinks by default and spends the output budget on it; grading needs the JSON answer (ADR-0032).
            options.extraBody(Map.of("thinking", Map.of("type", "disabled")));
        }
        if (provider == AiProvider.OPENROUTER) {
            // Route only to hosts that honour every parameter (JSON mode, reasoning effort) and keep no prompts
            // (ADR-0033).
            options.extraBody(OPENROUTER_ROUTING);
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
