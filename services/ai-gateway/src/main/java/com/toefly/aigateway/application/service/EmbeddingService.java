package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.in.EmbedUseCase;
import com.toefly.aigateway.application.port.out.EmbeddingPort;
import com.toefly.aigateway.application.port.out.RoutingTablePort;
import com.toefly.aigateway.application.port.out.TokenCounterPort;
import com.toefly.aigateway.domain.call.AiCallOutcome;
import com.toefly.aigateway.domain.call.TokenUsage;
import com.toefly.aigateway.domain.prompt.PromptRuleException;
import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.quota.QuotaDemand;
import com.toefly.aigateway.domain.routing.AiProvider;
import com.toefly.aigateway.domain.routing.AiUseCase;
import com.toefly.aigateway.domain.routing.ModelRef;
import com.toefly.aigateway.domain.routing.ModelRoute;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** Embeddings with the local model only (§2.2, route EMBEDDING): texts never leave, so no masking. */
@Service
class EmbeddingService implements EmbedUseCase {

    static final int MAX_TEXTS = 128;

    private final RoutingTablePort routing;
    private final EmbeddingPort embeddings;
    private final FallbackExecutor fallback;
    private final TokenCounterPort tokens;
    private final CallJournal journal;
    private final Clock clock;

    EmbeddingService(
            RoutingTablePort routing,
            EmbeddingPort embeddings,
            FallbackExecutor fallback,
            TokenCounterPort tokens,
            CallJournal journal,
            Clock clock) {
        this.routing = routing;
        this.embeddings = embeddings;
        this.fallback = fallback;
        this.tokens = tokens;
        this.journal = journal;
        this.clock = clock;
    }

    @Override
    public Embeddings embed(String callerService, List<String> texts, @Nullable UUID userId) {
        if (texts.isEmpty() || texts.size() > MAX_TEXTS) {
            throw new PromptRuleException("validation-failed", "send 1.." + MAX_TEXTS + " texts");
        }
        Instant started = clock.instant();
        String callId = journal.nextId();
        ModelRoute route = routing.current()
                .route(AiUseCase.EMBEDDING, null)
                .orElseThrow(() -> new PromptRuleException("validation-failed", "no route for EMBEDDING"));
        List<ModelRef> local = route.candidates().stream()
                .filter(model -> model.provider() == AiProvider.OLLAMA)
                .toList();
        if (local.isEmpty()) {
            throw new PromptRuleException("validation-failed", "the EMBEDDING route must stay local");
        }
        int inputTokens = tokens.estimate(String.join("\n", texts), local.getFirst());
        FallbackExecutor.Plan plan = new FallbackExecutor.Plan(
                AiUseCase.EMBEDDING,
                local,
                Priority.INTERACTIVE,
                userId,
                started.plusSeconds(30),
                model -> QuotaDemand.tokens(inputTokens),
                model -> Duration.ofSeconds(30));
        FallbackExecutor.Answer<List<float[]>> answer;
        try {
            answer = fallback.run(plan, (model, previous) -> embeddings.embed(model, texts), v -> TokenUsage.NONE);
        } catch (FallbackExecutor.Exhausted exhausted) {
            record(
                    callId,
                    callerService,
                    userId,
                    exhausted.lastModel(),
                    TokenUsage.NONE,
                    started,
                    exhausted.outcome(),
                    GenerationService.slug(exhausted.outcome()));
            throw exhausted.problem();
        }
        record(
                callId,
                callerService,
                userId,
                answer.model(),
                TokenUsage.tokens(inputTokens, 0),
                started,
                AiCallOutcome.SUCCESS,
                null);
        List<float[]> vectors = answer.value();
        return new Embeddings(
                vectors,
                vectors.isEmpty() ? 0 : vectors.getFirst().length,
                answer.model().model());
    }

    private void record(
            String callId,
            String caller,
            @Nullable UUID userId,
            ModelRef model,
            TokenUsage usage,
            Instant started,
            AiCallOutcome outcome,
            @Nullable String error) {
        journal.record(
                callId,
                new CallJournal.Entry(
                        caller,
                        AiUseCase.EMBEDDING,
                        null,
                        Priority.INTERACTIVE,
                        userId,
                        null,
                        null,
                        model,
                        0,
                        false,
                        false,
                        null,
                        null,
                        usage,
                        Duration.between(started, clock.instant()).toMillis(),
                        null,
                        outcome,
                        error,
                        null));
    }
}
