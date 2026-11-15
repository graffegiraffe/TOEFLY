package com.toefly.aigateway.domain.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutingTableTest {

    static final RouteParams PARAMS = new RouteParams(0.1, 1800, "medium", false, Duration.ofSeconds(60));
    static final ModelRef BIG = ModelRef.parse("GROQ:openai/gpt-oss-120b");
    static final ModelRef SMALL = ModelRef.parse("GROQ:openai/gpt-oss-20b");
    static final ModelRef QWEN = ModelRef.parse("GROQ:qwen/qwen3.8-27b");
    static final ModelRef LOCAL = ModelRef.parse("OLLAMA:gpt-oss:20b");

    @Test
    void parsesProviderAndModelWithColonsInTheName() {
        assertThat(LOCAL.provider()).isEqualTo(AiProvider.OLLAMA);
        assertThat(LOCAL.model()).isEqualTo("gpt-oss:20b");
        assertThat(LOCAL.family()).isEqualTo("OLLAMA:gpt-oss");
        assertThat(BIG.family()).isEqualTo("GROQ:gpt-oss-120b");
        assertThatIllegalArgumentException().isThrownBy(() -> ModelRef.parse("gpt-oss"));
    }

    @Test
    void passOfAGraderHasItsOwnRouteAndFallsBackToTheUseCase() {
        RoutingTable table = new RoutingTable(List.of(
                new ModelRoute(AiUseCase.WRITING_GRADING, null, List.of(BIG, SMALL, LOCAL), PARAMS),
                new ModelRoute(AiUseCase.WRITING_GRADING, "B", List.of(QWEN, SMALL, LOCAL), PARAMS)));

        assertThat(table.route(AiUseCase.WRITING_GRADING, "B")
                        .orElseThrow()
                        .candidates()
                        .getFirst())
                .isEqualTo(QWEN);
        assertThat(table.route(AiUseCase.WRITING_GRADING, "C")
                        .orElseThrow()
                        .candidates()
                        .getFirst())
                .isEqualTo(BIG);
        assertThat(table.route(AiUseCase.HINT, null)).isEmpty();
    }

    @Test
    void solverSkipsTheGeneratorModelAndOverrideGoesFirst() {
        ModelRoute route = new ModelRoute(AiUseCase.ITEM_SOLVING, null, List.of(QWEN, BIG, LOCAL), PARAMS);

        assertThat(route.order(null, "qwen/qwen3.8-27b")).containsExactly(BIG, LOCAL);
        assertThat(route.order(SMALL, null)).containsExactly(SMALL, QWEN, BIG, LOCAL);
    }

    @Test
    void duplicateRoutesAreRejected() {
        ModelRoute route = new ModelRoute(AiUseCase.HINT, null, List.of(SMALL), PARAMS);
        assertThatIllegalArgumentException().isThrownBy(() -> new RoutingTable(List.of(route, route)));
    }

    @Test
    void onlyGradingUseCasesAreMarkedDegradedOnTheReserve() {
        assertThat(AiUseCase.WRITING_GRADING.grades()).isTrue();
        assertThat(AiUseCase.TUTOR_CHAT.grades()).isFalse();
    }
}
