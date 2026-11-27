package com.toefly.aigateway.adapter.out.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.aigateway.application.port.out.ChatRequest;
import com.toefly.aigateway.application.port.out.ProviderFailure;
import com.toefly.aigateway.domain.chat.ChatMessage;
import com.toefly.aigateway.domain.routing.AiProvider;
import com.toefly.aigateway.domain.routing.ModelRef;
import com.toefly.aigateway.domain.routing.RouteParams;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;

class OpenAiCompatibleChatAdapterTest {

    static final RouteParams PARAMS = new RouteParams(0.1, 4000, "low", false, Duration.ofSeconds(60));

    static ChatRequest request(AiProvider provider, String model) {
        return new ChatRequest(
                new ModelRef(provider, model), List.of(ChatMessage.user("Grade this.")), PARAMS, "{}", List.of(), null);
    }

    static OpenAiCompatibleChatAdapter adapter(AiProvider provider, String key) {
        return new OpenAiCompatibleChatAdapter(
                provider, provider + "_API_KEY", "http://localhost:1", key, Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Z.ai calls switch GLM thinking off and get no reasoning effort (ADR-0032)")
    void zaiDisablesThinking() {
        OpenAiChatOptions options = (OpenAiChatOptions)
                adapter(AiProvider.ZAI, "k").options(request(AiProvider.ZAI, "glm-4.7-flash"), false);
        assertThat(options.getExtraBody()).containsEntry("thinking", Map.of("type", "disabled"));
        assertThat(options.getReasoningEffort()).isNull();
    }

    @Test
    @DisplayName("Groq gpt-oss calls keep the reasoning effort and no extra body")
    void groqKeepsReasoningEffort() {
        OpenAiChatOptions options = (OpenAiChatOptions)
                adapter(AiProvider.GROQ, "k").options(request(AiProvider.GROQ, "openai/gpt-oss-120b"), false);
        assertThat(options.getReasoningEffort()).isEqualTo("low");
        assertThat(options.getExtraBody() == null || options.getExtraBody().isEmpty())
                .isTrue();
    }

    @Test
    @DisplayName("streamed calls set no stream options, so Groq gets no include_obfuscation")
    void streamSendsNoObfuscationFlag() {
        OpenAiChatOptions options = (OpenAiChatOptions)
                adapter(AiProvider.GROQ, "k").options(request(AiProvider.GROQ, "openai/gpt-oss-20b"), true);
        assertThat(options.getStreamOptions()).isNull();
    }

    @Test
    @DisplayName("a missing key is refused with the name of its variable")
    void missingKeyIsRefused() {
        assertThatThrownBy(() -> adapter(AiProvider.ZAI, "").model(request(AiProvider.ZAI, "glm-4.7-flash")))
                .isInstanceOf(ProviderFailure.class)
                .hasMessageContaining("ZAI_API_KEY");
    }
}
