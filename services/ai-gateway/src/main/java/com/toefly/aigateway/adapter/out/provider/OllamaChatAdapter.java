package com.toefly.aigateway.adapter.out.provider;

import com.toefly.aigateway.application.port.out.ChatRequest;
import com.toefly.aigateway.domain.routing.AiProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/** The local reserve (§2.4, candidate 3) with Spring AI {@link OllamaChatModel}; no retries of its own. */
public class OllamaChatAdapter extends SpringAiChatSupport {

    private final OllamaChatModel model;

    public OllamaChatAdapter(OllamaChatModel model) {
        this.model = model;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.OLLAMA;
    }

    @Override
    protected ChatModel model(ChatRequest request) {
        return model;
    }

    @Override
    protected ChatOptions options(ChatRequest request, boolean stream) {
        OllamaChatOptions.Builder options = OllamaChatOptions.builder()
                .model(request.model().model())
                .temperature(request.params().temperature());
        if (request.params().maxOutputTokens() > 0) {
            options.numPredict(request.params().maxOutputTokens());
        }
        if (request.jsonSchema() != null) {
            options.format("json");
        }
        if (!request.tools().isEmpty()) {
            options.toolCallbacks(request.tools().stream()
                    .map(DefinitionOnlyTool::of)
                    .map(tool -> (org.springframework.ai.tool.ToolCallback) tool)
                    .toList());
        }
        return options.build();
    }
}
