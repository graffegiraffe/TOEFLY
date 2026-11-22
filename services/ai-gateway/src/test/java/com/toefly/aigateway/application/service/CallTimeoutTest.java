package com.toefly.aigateway.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.aigateway.domain.routing.AiProvider;
import com.toefly.aigateway.domain.routing.ModelRef;
import com.toefly.aigateway.domain.routing.RouteParams;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CallTimeoutTest {

    static final RouteParams ROUTE = new RouteParams(0.1, 1800, "medium", false, Duration.ofSeconds(60));

    @Test
    @DisplayName("Groq keeps the route timeout; Ollama gets 180 s and the free Z.ai tier 240 s (ADR-0032)")
    void slowProvidersGetLongerTimeouts() {
        assertThat(GenerationService.timeout(new ModelRef(AiProvider.GROQ, "openai/gpt-oss-120b"), ROUTE))
                .isEqualTo(Duration.ofSeconds(60));
        assertThat(GenerationService.timeout(new ModelRef(AiProvider.OLLAMA, "gpt-oss:20b"), ROUTE))
                .isEqualTo(Duration.ofSeconds(180));
        assertThat(GenerationService.timeout(new ModelRef(AiProvider.ZAI, "glm-4.5-flash"), ROUTE))
                .isEqualTo(Duration.ofSeconds(240));
    }
}
