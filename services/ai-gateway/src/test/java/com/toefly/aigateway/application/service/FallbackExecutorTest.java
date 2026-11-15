package com.toefly.aigateway.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.aigateway.application.port.in.AiOutputInvalidException;
import com.toefly.aigateway.application.port.in.AiProviderUnavailableException;
import com.toefly.aigateway.application.port.in.AiQuotaExhaustedException;
import com.toefly.aigateway.application.port.out.ProviderFailure;
import com.toefly.aigateway.domain.call.TokenUsage;
import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.quota.QuotaDemand;
import com.toefly.aigateway.domain.routing.AiUseCase;
import com.toefly.aigateway.domain.routing.ModelRef;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FallbackExecutorTest {

    final Gateway gateway = new Gateway();
    final List<ModelRef> tried = new ArrayList<>();

    FallbackExecutor.Plan plan(Priority priority) {
        return new FallbackExecutor.Plan(
                AiUseCase.WRITING_GRADING,
                List.of(Gateway.BIG, Gateway.SMALL, Gateway.LOCAL),
                priority,
                null,
                gateway.clock.instant().plusSeconds(600),
                model -> QuotaDemand.tokens(1000),
                model -> Duration.ofSeconds(60));
    }

    @Test
    void unavailableModelIsRepeatedOnceThenTheNextCandidateAnswersAndTheFallbackIsCounted() {
        var answer = gateway.fallback.run(
                plan(Priority.GRADING),
                (model, previous) -> {
                    tried.add(model);
                    if (model.equals(Gateway.BIG)) {
                        throw ProviderFailure.unavailable("503", null);
                    }
                    return "graded by " + model;
                },
                v -> TokenUsage.tokens(10, 10));

        assertThat(answer.depth()).isEqualTo(1);
        assertThat(tried).containsExactly(Gateway.BIG, Gateway.BIG, Gateway.SMALL);
        assertThat(gateway.meters
                        .counter(
                                "toefly.ai.fallback",
                                "use_case",
                                "WRITING_GRADING",
                                "depth",
                                "1",
                                "model",
                                Gateway.SMALL.toString())
                        .count())
                .isEqualTo(1);
    }

    @Test
    void rateLimitedModelIsPausedForRetryAfterAndSkippedAtOnce() {
        gateway.quotas.save(Fakes.tpm(Gateway.BIG, 6000));
        var answer = gateway.fallback.run(
                plan(Priority.INTERACTIVE),
                (model, previous) -> {
                    tried.add(model);
                    if (model.equals(Gateway.BIG)) {
                        throw new ProviderFailure(
                                ProviderFailure.Kind.RATE_LIMITED, "429", Duration.ofSeconds(17), null);
                    }
                    return "ok";
                },
                v -> TokenUsage.NONE);

        assertThat(answer.model()).isEqualTo(Gateway.SMALL);
        assertThat(tried).containsExactly(Gateway.BIG, Gateway.SMALL);
        assertThat(gateway.store.blocked.get(Gateway.BIG))
                .isEqualTo(gateway.clock.instant().plusSeconds(17));
    }

    @Test
    void answerThatFailsTheSchemaIsAskedAgainWithItsViolations() {
        List<String> seen = new ArrayList<>();
        var answer = gateway.fallback.run(
                plan(Priority.GRADING),
                (model, previous) -> {
                    seen.add(previous == null ? "first" : String.join(",", previous.violations()));
                    if (seen.size() < 3) {
                        throw new InvalidOutput(List.of("$.score: missing"), "{}", TokenUsage.tokens(5, 5));
                    }
                    return "valid";
                },
                v -> TokenUsage.NONE);

        assertThat(answer.model()).isEqualTo(Gateway.BIG);
        assertThat(seen).containsExactly("first", "$.score: missing", "$.score: missing");
    }

    @Test
    void schemaFailuresEverywhereEndIn422() {
        assertThatThrownBy(() -> gateway.fallback.run(
                        plan(Priority.GRADING),
                        (model, previous) -> {
                            throw new InvalidOutput(List.of("$.score: missing"), "{}", TokenUsage.NONE);
                        },
                        v -> TokenUsage.NONE))
                .isInstanceOfSatisfying(
                        FallbackExecutor.Exhausted.class,
                        e -> assertThat(e.problem()).isInstanceOf(AiOutputInvalidException.class));
    }

    @Test
    void quotaEverywhereEndsIn429WithTheEarliestRetry() {
        gateway.store.exhaust(Gateway.BIG, Duration.ofSeconds(40));
        gateway.store.exhaust(Gateway.SMALL, Duration.ofSeconds(25));
        gateway.store.exhaust(Gateway.LOCAL, Duration.ofSeconds(90));
        gateway.quotas.save(Fakes.tpm(Gateway.BIG, 6000));
        gateway.quotas.save(Fakes.tpm(Gateway.SMALL, 6000));
        gateway.quotas.save(Fakes.tpm(Gateway.LOCAL, 6000));

        assertThatThrownBy(() -> gateway.fallback.run(
                        plan(Priority.INTERACTIVE), (model, previous) -> "never", v -> TokenUsage.NONE))
                .isInstanceOfSatisfying(FallbackExecutor.Exhausted.class, e -> {
                    assertThat(e.problem()).isInstanceOf(AiQuotaExhaustedException.class);
                    assertThat(((AiQuotaExhaustedException) e.problem()).retryAfterSeconds())
                            .isBetween(1L, 25L);
                });
    }

    @Test
    void openBreakersAreSkippedAndAllDownIs503() {
        gateway.resilience.open.addAll(List.of(Gateway.BIG, Gateway.SMALL, Gateway.LOCAL));

        assertThatThrownBy(() -> gateway.fallback.run(
                        plan(Priority.GRADING), (model, previous) -> "never", v -> TokenUsage.NONE))
                .isInstanceOfSatisfying(
                        FallbackExecutor.Exhausted.class,
                        e -> assertThat(e.problem()).isInstanceOf(AiProviderUnavailableException.class));
    }
}
